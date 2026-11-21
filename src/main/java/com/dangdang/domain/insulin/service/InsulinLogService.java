package com.dangdang.domain.insulin.service;

import com.dangdang.domain.insulin.dto.request.InsulinLogCreateRequest;
import com.dangdang.domain.insulin.dto.request.InsulinLogUpdateRequest;
import com.dangdang.domain.insulin.exception.InsulinErrorCode;
import com.dangdang.domain.insulin.dto.response.InsulinLogPageResponse;
import com.dangdang.domain.insulin.dto.response.InsulinLogResponse;
import com.dangdang.domain.insulin.entity.InsulinLog;
import com.dangdang.domain.insulin.repository.InsulinLogRepository;
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

import java.time.Instant;
import java.time.LocalDate;

// 인슐린 투여 기록 (E-02 기록, D-10 고치기). 적은 그대로 저장만 하고 권장량 계산·투여 알림은 없음
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InsulinLogService {

    // 맞은 시각으로 받을 수 있는 가장 이른 시각: 한국 기준 2000-01-01 0시 (진료 날짜와 같은 범위)
    // "-1000000000-01-01T00:00:00Z" 같은 값도 Instant로 읽혀서, 그대로 두면 DB(TIMESTAMPTZ)에 못 넣어 500이 남
    private static final Instant MIN_TAKEN_AT = KoreanTime.startOfDay(LocalDate.of(QueryParams.MIN_YEAR, 1, 1));

    private final InsulinLogRepository insulinLogRepository;
    private final UserRepository userRepository;

    // 등록
    @Transactional
    public InsulinLogResponse create(Long userId, InsulinLogCreateRequest req) {
        checkTakenAt(req.takenAt());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        InsulinLog saved = insulinLogRepository.saveAndFlush(
                InsulinLog.of(user, req.insulinType(), req.units(), req.takenAt()));
        return InsulinLogResponse.from(saved);
    }

    // 목록: from·to는 한국 날짜 (둘 다 포함), 맞은 시각 오래된 순
    public InsulinLogPageResponse list(Long userId, LocalDate from, LocalDate to, int page, int size) {
        if (from.isAfter(to)) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, "from이 to보다 늦어요.");
        }
        return InsulinLogPageResponse.from(insulinLogRepository.findPage(userId,
                KoreanTime.startOfDay(from), KoreanTime.endOfDay(to), PageRequest.of(page, size)));
    }

    // 수정 (보낸 칸만). 바꿀 값이 없으면 INSULIN_40001, 없음·삭제됨·남의 기록이면 INSULIN_40401
    @Transactional
    public InsulinLogResponse update(Long userId, Long insulinLogId, InsulinLogUpdateRequest req) {
        if (req.isEmpty()) {
            throw new CustomException(InsulinErrorCode.NOTHING_TO_UPDATE);
        }
        checkTakenAt(req.takenAt());
        InsulinLog log = findMine(userId, insulinLogId);
        log.update(req.insulinType(), req.units(), req.takenAt());
        // 변경 감지로 트랜잭션 끝날 때 반영되지만, 응답에 새 updatedAt을 담으려고 먼저 DB에 씀
        insulinLogRepository.flush();
        return InsulinLogResponse.from(log);
    }

    // 내 것 + 삭제 안 된 것만. 없음·삭제됨·남의 것은 전부 404 (수정·삭제가 같이 씀)
    private InsulinLog findMine(Long userId, Long insulinLogId) {
        return insulinLogRepository.findByIdAndUserIdAndDeletedAtIsNull(insulinLogId, userId)
                .orElseThrow(() -> new CustomException(InsulinErrorCode.INSULIN_LOG_NOT_FOUND));
    }

    // 맞은 시각은 2000년 이후 (지금보다 뒤인지는 요청의 @PastOrPresent가 막음). 안 보냈으면(null) 통과
    private void checkTakenAt(Instant takenAt) {
        if (takenAt != null && takenAt.isBefore(MIN_TAKEN_AT)) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT,
                    "맞은 시각은 " + QueryParams.MIN_YEAR + "년 이후로 보내 주세요.");
        }
    }
}
