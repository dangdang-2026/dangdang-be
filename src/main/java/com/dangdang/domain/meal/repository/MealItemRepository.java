package com.dangdang.domain.meal.repository;

import com.dangdang.domain.meal.entity.MealItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// 식사 항목 DB 저장·조회
public interface MealItemRepository extends JpaRepository<MealItem, Long> {

    // 이 식사에 붙은 이 항목 + 삭제 안 된 것만 (다른 식사의 항목 id로는 못 찾음)
    Optional<MealItem> findByIdAndMealIdAndDeletedAtIsNull(Long id, Long mealId);
}
