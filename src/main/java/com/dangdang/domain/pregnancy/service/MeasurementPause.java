package com.dangdang.domain.pregnancy.service;

import com.dangdang.domain.pregnancy.enums.PregnancyState;
import com.dangdang.domain.pregnancy.repository.PregnancyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 측정 알림을 보내도 되는 상태인지 (PP-01, E-04). 공복·자기 전 알림 만들기와 모든 측정 알림 보내기가 같이 쓴다
@Component
@RequiredArgsConstructor
public class MeasurementPause {

    private final PregnancyRepository pregnancyRepository;

    // 임신 중이면 보낸다. 입원·다른 상황(PAUSED)·산후·졸업이면 멈춘다. 임신 정보를 아직 안 적은 사람은 보낸다
    @Transactional(readOnly = true)
    public boolean allowsMeasurement(Long userId) {
        return pregnancyRepository.findByUserIdAndStateNot(userId, PregnancyState.ENDED)
                .map(p -> p.getState() == PregnancyState.PREGNANT)
                .orElseGet(() -> !pregnancyRepository.existsByUserIdAndState(userId, PregnancyState.ENDED));
    }
}
