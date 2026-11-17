package com.dangdang.domain.mealplan.repository;

import com.dangdang.domain.mealplan.entity.MealSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealSettingRepository extends JpaRepository<MealSetting, Long> {
}
