package com.dangdang.domain.food.service;

import com.dangdang.domain.food.dto.response.FoodHistoryPageResponse;
import com.dangdang.domain.food.dto.response.FoodHistoryResponse;
import com.dangdang.domain.meal.dto.response.MealItemSummaryResponse;
import com.dangdang.domain.meal.entity.Meal;
import com.dangdang.domain.meal.repository.MealItemRepository;
import com.dangdang.domain.meal.repository.MealRepository;
import com.dangdang.domain.reading.dto.response.ReadingResponse;
import com.dangdang.domain.reading.entity.Reading;
import com.dangdang.domain.reading.repository.ReadingRepository;
import com.dangdang.domain.reading.service.ExpectedTimeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 같은 음식 지난 기록: 내가 이 음식을 먹었던 식사와 그 식사에 연결된 혈당
// 상관관계를 보여 줄 뿐이고, "이 음식 때문에 올랐다" 같은 판단은 만들지 않음
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FoodHistoryService {

    private final MealRepository mealRepository;
    private final MealItemRepository mealItemRepository;
    private final ReadingRepository readingRepository;
    private final ExpectedTimeResolver expectedTimeResolver;

    // foods에 그 코드가 있는지는 보지 않음
    // 식사 항목은 고른 그때의 값을 복사해 두므로, 식약처 데이터를 새로 넣어 코드가 사라져도 지난 기록은 보여 줌
    // 먹은 적이 없는 코드면 404가 아니라 items=[]
    public FoodHistoryPageResponse history(Long userId, String foodCode, int page, int size) {
        Page<Meal> meals = mealRepository.findPageEatenFood(userId, foodCode, PageRequest.of(page, size));
        List<Long> mealIds = meals.getContent().stream().map(Meal::getId).toList();
        if (mealIds.isEmpty()) {
            return FoodHistoryPageResponse.of(meals, List.of());
        }

        // 식사마다 따로 조회하지 않고 항목·혈당을 한 번에 읽어서 식사 id별로 나눔
        Map<Long, List<MealItemSummaryResponse>> itemsByMeal = mealItemRepository
                .findAllByMealIdInAndFoodCodeAndDeletedAtIsNullOrderByIdAsc(mealIds, foodCode).stream()
                .collect(Collectors.groupingBy(i -> i.getMeal().getId(),
                        Collectors.mapping(MealItemSummaryResponse::from, Collectors.toList())));
        List<Reading> readings = readingRepository
                .findAllByUserIdAndMealIdInAndDeletedAtIsNullOrderByMeasuredAtAscIdAsc(userId, mealIds);
        // 이 페이지 식사는 이미 읽었으니 넘겨서 또 조회하지 않게
        Map<Long, Instant> expectedAt = expectedTimeResolver.expectedAtByReading(userId, readings, meals.getContent());
        Map<Long, List<ReadingResponse>> readingsByMeal = readings.stream()
                .collect(Collectors.groupingBy(Reading::getMealId,
                        Collectors.mapping(r -> ReadingResponse.of(r, expectedAt.get(r.getId())), Collectors.toList())));

        List<FoodHistoryResponse> items = meals.getContent().stream()
                .map(m -> FoodHistoryResponse.of(m,
                        itemsByMeal.getOrDefault(m.getId(), List.of()),
                        readingsByMeal.getOrDefault(m.getId(), List.of())))
                .toList();
        return FoodHistoryPageResponse.of(meals, items);
    }
}
