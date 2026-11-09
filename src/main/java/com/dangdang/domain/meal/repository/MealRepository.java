package com.dangdang.domain.meal.repository;

import com.dangdang.domain.meal.entity.Meal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// 식사 기록 DB 저장·조회
public interface MealRepository extends JpaRepository<Meal, Long> {

    // 하나 찾기: 이 id + 내 것 + 삭제 안 된 것만 (아니면 Optional.empty)
    Optional<Meal> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
}
