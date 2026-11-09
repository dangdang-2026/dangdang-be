package com.dangdang.domain.meal.service;

import com.dangdang.domain.meal.dto.request.MealCreateRequest;
import com.dangdang.domain.meal.dto.response.MealResponse;
import com.dangdang.domain.meal.entity.Meal;
import com.dangdang.domain.meal.exception.MealErrorCode;
import com.dangdang.domain.meal.repository.MealRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MealService {

    private final MealRepository mealRepository;
    private final UserRepository userRepository;

    // 식사 시작 기록
    @Transactional
    public MealResponse create(Long userId, MealCreateRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        Meal saved = mealRepository.save(Meal.of(
                user, req.mealSlot(), req.startedAt(), req.isStartedOffline(), req.note()));
        return MealResponse.from(saved);
    }

    // 상세
    public MealResponse get(Long userId, Long mealId) {
        return MealResponse.from(findMine(userId, mealId));
    }

    // 내 것 + 삭제 안 된 것만. 없음·삭제됨·남의 것은 전부 404
    private Meal findMine(Long userId, Long mealId) {
        return mealRepository.findByIdAndUserIdAndDeletedAtIsNull(mealId, userId)
                .orElseThrow(() -> new CustomException(MealErrorCode.MEAL_NOT_FOUND));
    }
}
