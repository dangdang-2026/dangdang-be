package com.dangdang.domain.mealplan.entity;

import com.dangdang.domain.mealplan.enums.PortionUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// 식사 기록 설정 (M-20, 11-17 토론) — 한 사람 한 줄. 줄이 없으면 인분(SERVING)으로 본다
@Getter
@Entity
@Table(name = "meal_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealSetting {

    @Id
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PortionUnit portionUnit;

    @Column(nullable = false)
    private Instant updatedAt;

    // 저장은 MealSettingRepository.upsert (처음 바꾸는 요청이 동시에 와도 한 줄)
}
