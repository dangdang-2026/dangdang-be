package com.dangdang.domain.meal.repository;

import com.dangdang.domain.meal.entity.MealItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

// 식사 항목 DB 저장·조회
public interface MealItemRepository extends JpaRepository<MealItem, Long> {

    // 이 식사에 붙은 이 항목 + 삭제 안 된 것만 (다른 식사의 항목 id로는 못 찾음)
    Optional<MealItem> findByIdAndMealIdAndDeletedAtIsNull(Long id, Long mealId);

    // 여러 식사의 살아 있는 항목을 한 번에 (식사마다 따로 조회하지 않게). 추가한 순서대로
    List<MealItem> findAllByMealIdInAndDeletedAtIsNullOrderByIdAsc(Collection<Long> mealIds);

    // 여러 식사의 살아 있는 항목 중 이 음식(foodCode)만 한 번에 (같은 음식 지난 기록용). 추가한 순서대로
    List<MealItem> findAllByMealIdInAndFoodCodeAndDeletedAtIsNullOrderByIdAsc(Collection<Long> mealIds, String foodCode);
}
