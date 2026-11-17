package com.dangdang.domain.mealplan.service;

import com.dangdang.domain.mealplan.dto.response.MealSettingResponse;
import com.dangdang.domain.mealplan.entity.MealSetting;
import com.dangdang.domain.mealplan.enums.PortionUnit;
import com.dangdang.domain.mealplan.repository.MealSettingRepository;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

// 식사 기록 설정 (M-20). 양은 참고 영양값 계산에만 쓰고, 많다·적다를 말하지 않는다
@Service
@RequiredArgsConstructor
public class MealSettingService {

    private final MealSettingRepository mealSettingRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    // 바꾼 적 없으면 줄을 만들지 않고 SERVING으로 답한다
    @Transactional(readOnly = true)
    public MealSettingResponse get(Long userId) {
        PortionUnit unit = mealSettingRepository.findById(userId)
                .map(MealSetting::getPortionUnit).orElse(PortionUnit.SERVING);
        return new MealSettingResponse(unit);
    }

    @Transactional
    public MealSettingResponse change(Long userId, PortionUnit portionUnit) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException(UserErrorCode.USER_NOT_FOUND);
        }
        Instant now = clock.instant();
        MealSetting setting = mealSettingRepository.findById(userId)
                .orElseGet(() -> mealSettingRepository.save(MealSetting.of(userId, now)));
        setting.changePortionUnit(portionUnit, now);
        return new MealSettingResponse(setting.getPortionUnit());
    }
}
