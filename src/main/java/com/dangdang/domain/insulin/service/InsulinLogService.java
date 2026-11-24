package com.dangdang.domain.insulin.service;

import com.dangdang.domain.insulin.dto.request.InsulinLogCreateRequest;
import com.dangdang.domain.insulin.dto.request.InsulinLogUpdateRequest;
import com.dangdang.domain.insulin.dto.response.InsulinLogPageResponse;
import com.dangdang.domain.insulin.dto.response.InsulinLogResponse;
import com.dangdang.domain.insulin.entity.InsulinLog;
import com.dangdang.domain.insulin.enums.InsulinType;
import com.dangdang.domain.insulin.exception.InsulinErrorCode;
import com.dangdang.domain.insulin.repository.InsulinLogRepository;
import com.dangdang.domain.meal.enums.MealSlot;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.common.KoreanTime;
import com.dangdang.global.common.QueryParams;
import com.dangdang.global.exception.CustomException;
import com.dangdang.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

// 인슐린 투여 기록 (E-02 기록, D-10 고치기). 적은 그대로 저장만 하고 권장량 계산·투여 알림은 없음
// 관리 모드(「인슐린 함께」)는 확인하지 않음 — 모드를 바꿔도 지난 기록은 그대로 보여야 해서
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InsulinLogService {

    // 맞은 시각으로 받을 수 있는 가장 이른 시각: 한국 기준 2000-01-01 0시 (진료 날짜와 같은 범위)
    // "-1000000000-01-01T00:00:00Z" 같은 값도 Instant로 읽혀서, 그대로 두면 DB(TIMESTAMPTZ)에 못 넣어 500이 남
    private static final Instant MIN_TAKEN_AT = KoreanTime.startOfDay(LocalDate.of(QueryParams.MIN_YEAR, 1, 1));

    private final InsulinLogRepository insulinLogRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    // 등록
    @Transactional
    public InsulinLogResponse create(Long userId, InsulinLogCreateRequest req) {
        checkTakenAt(req.takenAt());
        MealSlot mealSlot = mealSlotFor(req.insulinType(), req.mealSlot(), null);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        InsulinLog saved = insulinLogRepository.saveAndFlush(
                InsulinLog.of(user, req.insulinType(), mealSlot, req.units(), req.takenAt()));
        return InsulinLogResponse.from(saved);
    }

    // 목록: from·to는 한국 날짜 (둘 다 포함), 맞은 시각 오래된 순
    public InsulinLogPageResponse list(Long userId, LocalDate from, LocalDate to, int page, int size) {
        if (from.isAfter(to)) {
            throw new CustomException(InsulinErrorCode.INVALID_DATE_RANGE);
        }
        return InsulinLogPageResponse.from(insulinLogRepository.findPage(userId,
                KoreanTime.startOfDay(from), KoreanTime.endOfDay(to), PageRequest.of(page, size)));
    }

    // 지난 기록 불러오기 (E-02 「지난 기록 · 어제 저녁 식사 때 6단위 불러오기」)
    // 같은 종류(식사 때면 같은 끼니까지) 중 맞은 시각이 가장 늦은 1건. 없으면 null (응답 data 없음)
    public InsulinLogResponse latest(Long userId, InsulinType insulinType, MealSlot mealSlot) {
        MealSlot slot = mealSlotFor(insulinType, mealSlot, null);   // 등록과 같은 규칙 (식사 때면 끼니 필수)
        Optional<InsulinLog> found = slot == null
                ? insulinLogRepository
                        .findFirstByUserIdAndInsulinTypeAndMealSlotIsNullAndDeletedAtIsNullOrderByTakenAtDescIdDesc(
                                userId, insulinType)
                : insulinLogRepository
                        .findFirstByUserIdAndInsulinTypeAndMealSlotAndDeletedAtIsNullOrderByTakenAtDescIdDesc(
                                userId, insulinType, slot);
        return found.map(InsulinLogResponse::from).orElse(null);
    }

    // 수정 (보낸 칸만). 종류를 바꾸면 끼니도 그 종류에 맞게 정리함 (MEAL → BEDTIME이면 끼니를 지움)
    // 바꿀 값이 없으면 INSULIN_40002, 없음·삭제됨·남의 기록이면 INSULIN_40401
    @Transactional
    public InsulinLogResponse update(Long userId, Long insulinLogId, InsulinLogUpdateRequest req) {
        if (req.isEmpty()) {
            throw new CustomException(InsulinErrorCode.NOTHING_TO_UPDATE);
        }
        checkTakenAt(req.takenAt());
        InsulinLog log = findMine(userId, insulinLogId);
        InsulinType type = req.insulinType() != null ? req.insulinType() : log.getInsulinType();
        MealSlot mealSlot = mealSlotFor(type, req.mealSlot(), log.getMealSlot());

        log.update(type, mealSlot, req.units(), req.takenAt(), clock.instant());
        // 변경 감지로 트랜잭션 끝날 때 반영되지만, 응답에 새 updatedAt을 담으려고 먼저 DB에 씀
        insulinLogRepository.flush();
        return InsulinLogResponse.from(log);
    }

    // 삭제 (soft delete). 이미 지운 기록을 또 지우면 findMine에서 404 (혈당 기록 삭제와 같음)
    @Transactional
    public void delete(Long userId, Long insulinLogId) {
        findMine(userId, insulinLogId).delete(clock.instant());   // 변경 감지로 트랜잭션 끝날 때 반영됨
    }

    // 내 것 + 삭제 안 된 것만, 줄을 잠그고 읽음. 없음·삭제됨·남의 것은 전부 404 (수정·삭제가 같이 씀)
    private InsulinLog findMine(Long userId, Long insulinLogId) {
        return insulinLogRepository.findMineForUpdate(insulinLogId, userId)
                .orElseThrow(() -> new CustomException(InsulinErrorCode.INSULIN_LOG_NOT_FOUND));
    }

    // 맞은 시각은 2000년 이후 (지금보다 뒤인지는 요청의 @PastOrPresent가 막음). 안 보냈으면(null) 통과
    private void checkTakenAt(Instant takenAt) {
        if (takenAt != null && takenAt.isBefore(MIN_TAKEN_AT)) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT,
                    "맞은 시각은 " + QueryParams.MIN_YEAR + "년 이후로 보내 주세요.");
        }
    }

    // 종류에 맞는 끼니를 정함. sent = 이번에 보낸 끼니(안 보냈으면 null), current = 지금 저장된 끼니(등록·지난 기록이면 null)
    // - MEAL: 보낸 끼니가 있으면 그것, 안 보냈으면 지금 끼니. 둘 다 없으면 400, 간식이면 400
    // - BEDTIME: 끼니가 없어야 함. 보냈으면 400, 안 보냈으면 null (MEAL에서 바꿨으면 지워짐)
    private static MealSlot mealSlotFor(InsulinType type, MealSlot sent, MealSlot current) {
        if (type != InsulinType.MEAL) {
            if (sent != null) {
                throw new CustomException(InsulinErrorCode.MEAL_SLOT_NOT_ALLOWED);
            }
            return null;
        }
        MealSlot slot = sent != null ? sent : current;
        if (slot == null) {
            throw new CustomException(InsulinErrorCode.MEAL_SLOT_REQUIRED);
        }
        if (slot == MealSlot.SNACK) {
            throw new CustomException(InsulinErrorCode.SNACK_NOT_ALLOWED);
        }
        return slot;
    }
}
