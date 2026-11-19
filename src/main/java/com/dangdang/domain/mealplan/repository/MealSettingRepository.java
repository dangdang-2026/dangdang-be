package com.dangdang.domain.mealplan.repository;

import com.dangdang.domain.mealplan.entity.MealSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface MealSettingRepository extends JpaRepository<MealSetting, Long> {

    // 없으면 만들고 있으면 바꾼다 — 처음 바꾸는 요청이 동시에 두 번 와도 한 줄 (PK 중복 500 없음)
    @Modifying(clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO meal_settings (user_id, portion_unit, updated_at)
            VALUES (:userId, :portionUnit, :now)
            ON CONFLICT (user_id) DO UPDATE SET portion_unit = EXCLUDED.portion_unit, updated_at = EXCLUDED.updated_at
            """)
    void upsert(@Param("userId") Long userId, @Param("portionUnit") String portionUnit, @Param("now") Instant now);
}
