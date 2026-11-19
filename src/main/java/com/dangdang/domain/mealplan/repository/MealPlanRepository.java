package com.dangdang.domain.mealplan.repository;

import com.dangdang.domain.mealplan.entity.MealPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public interface MealPlanRepository extends JpaRepository<MealPlan, Long> {

    Optional<MealPlan> findByUserId(Long userId);

    // 한 사람 한 장 — 없으면 만들고 있으면 통째로 바꾼다. 처음 저장이 동시에 두 번 와도 user_id UNIQUE 500 없음
    // CAST: 값이 null일 때도 칸 타입을 DB가 알 수 있게
    @Modifying(clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO meal_plans (user_id, care_site_id, received_on, meal_guide, snack_guide, created_at, updated_at)
            VALUES (:userId, CAST(:careSiteId AS BIGINT), CAST(:receivedOn AS DATE),
                    CAST(:mealGuide AS VARCHAR), CAST(:snackGuide AS VARCHAR), :now, :now)
            ON CONFLICT (user_id) DO UPDATE SET care_site_id = EXCLUDED.care_site_id,
                    received_on = EXCLUDED.received_on, meal_guide = EXCLUDED.meal_guide,
                    snack_guide = EXCLUDED.snack_guide, updated_at = EXCLUDED.updated_at
            """)
    void upsert(@Param("userId") Long userId, @Param("careSiteId") Long careSiteId,
                @Param("receivedOn") LocalDate receivedOn, @Param("mealGuide") String mealGuide,
                @Param("snackGuide") String snackGuide, @Param("now") Instant now);
}
