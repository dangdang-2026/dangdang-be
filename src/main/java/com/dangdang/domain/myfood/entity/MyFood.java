package com.dangdang.domain.myfood.entity;

import com.dangdang.domain.meal.entity.MealNutrition;
import com.dangdang.domain.myfood.enums.UsualQuantity;
import com.dangdang.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Getter
@Entity
@Table(name = "my_foods")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MyFood {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 30)
    private String similarFoodCode;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private UsualQuantity usualQuantity;

    @JdbcTypeCode(SqlTypes.SMALLINT)         // DB 칸이 SMALLINT라서
    private Integer usualGrams;              // 보통 먹는 양 g (선택)

    @JdbcTypeCode(SqlTypes.JSON)
    private MealNutrition nutritionOverride;   // 식사 항목과 같은 타입 (모르는 칸은 읽을 때 무시)

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static MyFood of(User user, String name, String similarFoodCode, UsualQuantity usualQuantity,
                            Integer usualGrams, MealNutrition nutrition, Instant now) {
        MyFood f = new MyFood();
        f.user = user;
        f.name = name;
        f.similarFoodCode = similarFoodCode;
        f.usualQuantity = usualQuantity;
        f.usualGrams = usualGrams;
        f.nutritionOverride = nutrition;
        f.createdAt = now;
        f.updatedAt = now;
        return f;
    }

    // PATCH: 보낸 칸만. clear… = true면 그 칸을 비운다 (null을 「안 보냄」과 구분하려고)
    public void change(String name, String similarFoodCode, boolean clearSimilar, UsualQuantity usualQuantity,
                       boolean clearQuantity, Integer usualGrams, boolean clearGrams,
                       MealNutrition nutrition, boolean clearNutrition, Instant now) {
        if (name != null) this.name = name;
        if (clearSimilar) this.similarFoodCode = null;
        else if (similarFoodCode != null) this.similarFoodCode = similarFoodCode;
        if (clearQuantity) this.usualQuantity = null;
        else if (usualQuantity != null) this.usualQuantity = usualQuantity;
        if (clearGrams) this.usualGrams = null;
        else if (usualGrams != null) this.usualGrams = usualGrams;
        if (clearNutrition) this.nutritionOverride = null;
        else if (nutrition != null) this.nutritionOverride = nutrition;
        this.updatedAt = now;
    }
}
