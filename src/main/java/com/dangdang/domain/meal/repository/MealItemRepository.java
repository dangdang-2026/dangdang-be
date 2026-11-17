package com.dangdang.domain.meal.repository;

import com.dangdang.domain.meal.entity.MealItem;
import org.springframework.data.jpa.repository.JpaRepository;

// 식사 항목 DB 저장·조회
public interface MealItemRepository extends JpaRepository<MealItem, Long> {
}
