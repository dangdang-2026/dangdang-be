package com.dangdang.domain.mealplan.repository;

import com.dangdang.domain.mealplan.entity.MealPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MealPlanRepository extends JpaRepository<MealPlan, Long> {

    Optional<MealPlan> findByUserId(Long userId);
}
