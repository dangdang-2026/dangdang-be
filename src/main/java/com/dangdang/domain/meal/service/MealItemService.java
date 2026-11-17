package com.dangdang.domain.meal.service;

import com.dangdang.domain.food.entity.Food;
import com.dangdang.domain.food.exception.FoodErrorCode;
import com.dangdang.domain.food.repository.FoodRepository;
import com.dangdang.domain.meal.dto.request.MealItemCreateRequest;
import com.dangdang.domain.meal.dto.request.MealItemUpdateRequest;
import com.dangdang.domain.meal.dto.response.MealItemResponse;
import com.dangdang.domain.meal.entity.Meal;
import com.dangdang.domain.meal.entity.MealItem;
import com.dangdang.domain.meal.exception.MealErrorCode;
import com.dangdang.domain.meal.repository.MealItemRepository;
import com.dangdang.domain.meal.repository.MealRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

// 식사에 붙는 음식 항목
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MealItemService {

    private final MealRepository mealRepository;
    private final MealItemRepository mealItemRepository;
    private final FoodRepository foodRepository;
    private final Clock clock;

    // 항목 추가. 식약처 음식(foodCode)을 고르거나 이름(name)을 직접 적음 (둘 중 하나만)
    @Transactional
    public MealItemResponse add(Long userId, Long mealId, MealItemCreateRequest req) {
        Meal meal = findMyMeal(userId, mealId);
        String foodCode = req.trimmedFoodCode();
        String name = req.trimmedName();
        if ((foodCode == null) == (name == null)) {
            throw new CustomException(MealErrorCode.ITEM_FOOD_OR_NAME);
        }

        MealItem item;
        if (foodCode != null) {
            // 고른 음식의 영양값은 공공 값을 복사함. 고치려면 영양값 직접 입력 API로 (명세대로 따로 둠)
            if (req.nutrition() != null) {
                throw new CustomException(MealErrorCode.ITEM_NUTRITION_WITH_FOOD);
            }
            Food food = foodRepository.findById(foodCode)
                    .orElseThrow(() -> new CustomException(FoodErrorCode.FOOD_NOT_FOUND));
            item = MealItem.ofFood(meal, food, req.servingsOrDefault(), req.weightG());
        } else {
            item = MealItem.ofCustom(meal, name, req.servingsOrDefault(), req.weightG(),
                    req.nutrition() == null ? null : req.nutrition().toNutrition());
        }
        mealItemRepository.save(item);
        mealRepository.touch(mealId, clock.instant());   // 항목이 바뀌면 식사도 바뀐 것
        return MealItemResponse.from(item);
    }

    // 항목 수정 (보낸 칸만). 인분·무게, 직접 적은 음식이면 이름까지
    @Transactional
    public MealItemResponse update(Long userId, Long mealId, Long itemId, MealItemUpdateRequest req) {
        if (req.isEmpty()) {
            throw new CustomException(MealErrorCode.NOTHING_TO_UPDATE);
        }
        MealItem item = findMyItem(userId, mealId, itemId);
        String name = req.trimmedName();
        if (name != null && !item.isCustom()) {
            throw new CustomException(MealErrorCode.ITEM_NAME_OF_FOOD);
        }
        item.update(req.getServings(), req.isWeightGSent(), req.getWeightG(), name);
        mealRepository.touch(mealId, clock.instant());   // 여기서 항목 변경도 먼저 DB에 써서 응답에 새 updatedAt이 담김
        return MealItemResponse.from(item);
    }

    // 항목 삭제 (soft delete). 이미 지운 항목을 또 지우면 404
    @Transactional
    public void delete(Long userId, Long mealId, Long itemId) {
        findMyItem(userId, mealId, itemId).delete(clock.instant());
        mealRepository.touch(mealId, clock.instant());   // 항목이 바뀌면 식사도 바뀐 것
    }

    // 내 식사의 살아 있는 항목. 식사·항목이 없거나 남의 것이면 404
    private MealItem findMyItem(Long userId, Long mealId, Long itemId) {
        findMyMeal(userId, mealId);
        return mealItemRepository.findByIdAndMealIdAndDeletedAtIsNull(itemId, mealId)
                .orElseThrow(() -> new CustomException(MealErrorCode.ITEM_NOT_FOUND));
    }

    // 내 식사 + 삭제 안 된 것만. 없음·삭제됨·남의 것은 전부 404
    private Meal findMyMeal(Long userId, Long mealId) {
        return mealRepository.findByIdAndUserIdAndDeletedAtIsNull(mealId, userId)
                .orElseThrow(() -> new CustomException(MealErrorCode.MEAL_NOT_FOUND));
    }
}
