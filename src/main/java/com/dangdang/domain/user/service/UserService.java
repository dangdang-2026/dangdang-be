package com.dangdang.domain.user.service;

import com.dangdang.domain.pregnancy.entity.Pregnancy;
import com.dangdang.domain.pregnancy.enums.PregnancyState;
import com.dangdang.domain.pregnancy.repository.PregnancyRepository;
import com.dangdang.domain.user.dto.response.MeResponse;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ConsentService consentService;
    private final PregnancyRepository pregnancyRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MeResponse getMe(Long userId) {
        // 진행 중인 임신(ENDED가 아닌 줄)의 예정일 — M-02,H-14,오늘 화면 머리말 「임신 26주 3일」
        Pregnancy pregnancy = pregnancyRepository.findByUserIdAndStateNot(userId, PregnancyState.ENDED).orElse(null);
        return userRepository.findById(userId)
                .map(user -> MeResponse.from(user, consentService.isCompleted(userId), pregnancy, LocalDate.now(clock)))
                .orElseThrow(()->new CustomException(UserErrorCode.USER_NOT_FOUND));
    }
}
