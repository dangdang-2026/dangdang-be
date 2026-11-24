package com.dangdang.domain.user.service;

import com.dangdang.domain.pregnancy.entity.Pregnancy;
import com.dangdang.domain.pregnancy.enums.CareMode;
import com.dangdang.domain.pregnancy.enums.PregnancyState;
import com.dangdang.domain.pregnancy.exception.PregnancyErrorCode;
import com.dangdang.domain.pregnancy.repository.PregnancyRepository;
import com.dangdang.domain.user.dto.response.PregnancyProfileResponse;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.common.KoreanTime;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

// 진단일 · 관리 모드 · 연락 기준 메모 (M-02 · M-03). 셋 다 진행 중인 임신 줄에 저장한다
// 셋 다 건강정보라 건강정보 동의가 없으면 403 (철회하면 pregnancies 줄째 지워진다)
@Slf4j
@Service
@RequiredArgsConstructor
public class PregnancyProfileService {

    private final UserRepository userRepository;
    private final PregnancyRepository pregnancyRepository;
    private final Clock clock;
    private final SensitiveConsentGuard sensitiveConsentGuard;

    @Transactional
    public PregnancyProfileResponse changeDiagnosisDate(Long userId, LocalDate diagnosisDate) {
        User user = lockUser(userId);
        Instant now = clock.instant();
        if (diagnosisDate.isAfter(KoreanTime.dateOf(now))) {
            throw new CustomException(PregnancyErrorCode.FUTURE_DIAGNOSIS_DATE);   // 진단은 이미 일어난 일 (오늘은 한국 날짜)
        }
        Pregnancy p = activeOrStart(user, now);
        if (p.getDueDate() != null && diagnosisDate.isAfter(p.getDueDate())) {
            throw new CustomException(PregnancyErrorCode.DIAGNOSIS_AFTER_DUE_DATE);   // 같은 날은 된다
        }
        p.changeDiagnosisDate(diagnosisDate, now);
        return PregnancyProfileResponse.from(p);
    }

    @Transactional
    public PregnancyProfileResponse changeCareMode(Long userId, CareMode careMode) {
        User user = lockUser(userId);
        Instant now = clock.instant();
        Pregnancy p = activeOrStart(user, now);
        p.changeCareMode(careMode, now);
        log.info("[임신 정보] 관리 모드 변경 - 사용자: {}, 모드: {}", userId, careMode);
        return PregnancyProfileResponse.from(p);
    }

    @Transactional
    public PregnancyProfileResponse changeContactMemo(Long userId, String contactMemo) {
        User user = lockUser(userId);
        Instant now = clock.instant();
        Pregnancy p = activeOrStart(user, now);
        p.changeContactMemo(contactMemo, now);   // 메모 내용은 사용자가 쓴 건강 관련 글이라 로그에 남기지 않는다
        return PregnancyProfileResponse.from(p);
    }

    // 이 사람 줄을 잠그고 건강정보 동의를 본다 (PostpartumService·DueDateService와 같은 잠금)
    // 저장을 두 번 빠르게 눌러도 차례로 → 임신 줄이 둘 생겨 ux_pregnancies_active에 걸리지 않고, 앞 요청이 바꾼 칸도 덮어쓰지 않는다
    private User lockUser(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        sensitiveConsentGuard.require(userId);   // 건강정보 동의가 없으면 403 (위에서 이미 더 센 잠금을 잡음)
        return user;
    }

    // 진행 중인 임신 줄 (PREGNANT · HOSPITALIZED · PAUSED · POSTPARTUM). 예정일을 안 적은 사람이면 지금 한 줄 만든다
    // 졸업한 뒤에는 고칠 임신이 없어서 409
    private Pregnancy activeOrStart(User user, Instant now) {
        Pregnancy active = pregnancyRepository.findByUserIdAndStateNot(user.getId(), PregnancyState.ENDED).orElse(null);
        if (active != null) {
            return active;
        }
        if (pregnancyRepository.existsByUserIdAndState(user.getId(), PregnancyState.ENDED)) {
            throw new CustomException(PregnancyErrorCode.ALREADY_GRADUATED);
        }
        return pregnancyRepository.save(Pregnancy.start(user, null, now));
    }
}
