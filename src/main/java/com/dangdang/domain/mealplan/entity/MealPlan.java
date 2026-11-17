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

    private LocalDate receivedOn;

    @Column(length = 2000)
    private String mealGuide;

    @Column(length = 1000)
    private String snackGuide;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static MealPlan of(User user, Instant now) {
        MealPlan p = new MealPlan();
        p.user = user;
        p.createdAt = now;
        return p;
    }

    // PUT — 통째로 바꾼다 (안 보낸 칸은 비움)
    public void replace(Long careSiteId, LocalDate receivedOn, String mealGuide, String snackGuide, Instant now) {
        this.careSiteId = careSiteId;
        this.receivedOn = receivedOn;
        this.mealGuide = mealGuide;
        this.snackGuide = snackGuide;
        this.updatedAt = now;
    }
}
