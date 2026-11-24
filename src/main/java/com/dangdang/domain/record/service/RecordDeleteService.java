package com.dangdang.domain.record.service;

import com.dangdang.domain.insulin.repository.InsulinLogRepository;
import com.dangdang.domain.meal.repository.MealRepository;
import com.dangdang.domain.reading.repository.ReadingRepository;
import com.dangdang.domain.record.dto.response.RecordDeleteResponse;
import com.dangdang.domain.reminder.repository.ReminderRepository;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.common.KoreanTime;
import com.dangdang.global.exception.CustomException;
import com.dangdang.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

// 기간 골라 기록 지우기 (M-07 「기록 지우기」). 하나 지우기와 같은 규칙: 소프트 삭제 + 지운 식사의 안 보낸 식후 알림 취소
// 혈당·식사·인슐린을 지운다. 되돌리기는 없음 (화면이 빨간 시트로 한 번 더 묻는다)
// 지우기라 건강정보 동의 확인(SensitiveConsentGuard)은 안 부른다 (동의를 철회한 사람도 지울 수 있어야 함)
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordDeleteService {

    // 한 번에 지울 수 있는 최대 기간 (1년, 양 끝 포함). 실수로 「전체」를 지우는 걸 한 번 막고, 한 요청이 너무 커지지 않게
    static final int MAX_RANGE_DAYS = 366;

    private final ReadingRepository readingRepository;
    private final MealRepository mealRepository;
    private final InsulinLogRepository insulinLogRepository;
    private final ReminderRepository reminderRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public RecordDeleteResponse deleteInRange(Long userId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, "from은 to보다 늦을 수 없어요.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, "한 번에 " + MAX_RANGE_DAYS + "일까지 지울 수 있어요.");
        }
        Instant start = KoreanTime.startOfDay(from);
        Instant end = KoreanTime.endOfDay(to);
        Instant now = clock.instant();
        // 이 사람 줄을 FOR SHARE로 잡음 (동의 확인은 안 함 — 지우기는 동의가 없어도 됨)
        // 지난 날짜 기준 등록(FOR UPDATE를 잡고 혈당을 다시 붙임)과 차례로 돌아서, 두 벌크 UPDATE가 같은 혈당 줄들을 서로 다른 순서로 잠그다 막히지 않게
        userRepository.findByIdForShare(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        int readings = readingRepository.softDeleteInRange(userId, start, end, now);
        int insulinLogs = insulinLogRepository.softDeleteInRange(userId, start, end, now);
        List<Long> mealIds = mealRepository.findIdsInRange(userId, start, end);
        int meals = 0;
        if (!mealIds.isEmpty()) {
            meals = mealRepository.softDeleteAll(mealIds, userId, now);
            reminderRepository.cancelScheduledForMeals(mealIds);   // 지운 식사의 식후 알림이 울리지 않게
        }
        log.info("[기록] 기간 지우기 - 사용자: {}, {} ~ {}, 혈당 {}개 · 식사 {}개 · 인슐린 {}개",
                userId, from, to, readings, meals, insulinLogs);
        return new RecordDeleteResponse(from, to, readings, meals, insulinLogs);
    }
}
