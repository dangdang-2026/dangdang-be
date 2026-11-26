package com.dangdang.domain.mealplan.entity;

import com.dangdang.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Entity
@Table(name = "meal_plans")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private Long careSiteId;

    @Column(length = 50)
    private String placeName;                // 받은 곳을 글로 적은 것 (병원 목록에 없는 곳). 없으면 null

    private LocalDate receivedOn;

    @Column(length = 2000)
    private String mealGuide;

    @Column(length = 1000)
    private String snackGuide;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    // 저장은 MealPlanRepository.upsert (한 사람 한 장, 동시 저장에도 한 줄)
}
