package com.dangdang.domain.meal.entity;

import com.dangdang.domain.food.entity.Food;
import com.dangdang.domain.meal.enums.NutritionSource;
import com.dangdang.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

// meal_items 테이블 한 줄 = 식사에 붙은 음식 하나
// 식약처 음식을 고르면 그때의 영양값(100g·100ml당)을 복사해 둠 → foods가 나중에 바뀌어도 지난 기록 값은 그대로
@Getter
@Entity
@Table(name = "meal_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealItem extends BaseTimeEntity {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meal_id")
    private Meal meal;

    @Column(length = 30)
    private String foodCode;                 // 식약처 음식이면 코드, 직접 적은 음식이면 null

    @Column(nullable = false, length = 100)
    private String name;                     // 보이는 이름

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal servings;             // 먹은 양 (인분)

    @Column(precision = 6, scale = 1)
    private BigDecimal weight;               // 직접 잰 먹은 양 (unit 단위). 있으면 servings 대신 씀

    @Column(nullable = false, length = 2)
    private String unit;                     // g / ml

    // 고른 그때의 foods 값 복사 (100g·100ml당). 직접 적은 음식이면 전부 null
    @Column(precision = 7, scale = 1)
    private BigDecimal servingAmount;        // 1인분 양

    @Column(precision = 7, scale = 2)
    private BigDecimal kcal;

    @Column(name = "carb_g", precision = 7, scale = 2)   // 이름 끝 대문자 하나는 자동으로 _g가 안 돼서 직접 적음
    private BigDecimal carbG;

    @Column(name = "sugar_g", precision = 7, scale = 2)
    private BigDecimal sugarG;

    @Column(name = "protein_g", precision = 7, scale = 2)
    private BigDecimal proteinG;

    @Column(name = "fat_g", precision = 7, scale = 2)
    private BigDecimal fatG;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private NutritionSource nutritionSource;

    @JdbcTypeCode(SqlTypes.JSON)             // DB 칸이 JSONB라서
    private MealNutrition nutritionOverride; // 직접 적은 영양값 (먹은 양 전체 기준)

    private Instant deletedAt;               // null = 살아 있음, 값 있음 = 삭제됨

    // 식약처 음식을 골라서 추가. 그때의 영양값을 복사함
    public static MealItem ofFood(Meal meal, Food food, BigDecimal servings, BigDecimal weight) {
        MealItem i = new MealItem();
        i.meal = meal;
        i.foodCode = food.getFoodCode();
        i.name = food.displayName();
        i.servings = servings;
        i.weight = weight;
        i.unit = food.getBaseUnit();
        i.servingAmount = food.getServingAmount();
        i.kcal = food.getKcal();
        i.carbG = food.getCarbG();
        i.sugarG = food.getSugarG();
        i.proteinG = food.getProteinG();
        i.fatG = food.getFatG();
        i.nutritionSource = NutritionSource.DB;
        return i;
    }

    // 이름을 직접 적어서 추가. 영양값은 사용자가 적은 것만 있음 (안 적으면 전부 모름)
    public static MealItem ofCustom(Meal meal, String name, BigDecimal servings, BigDecimal weight, MealNutrition nutrition) {
        MealItem i = new MealItem();
        i.meal = meal;
        i.name = name;
        i.servings = servings;
        i.weight = weight;
        i.unit = "g";
        i.nutritionSource = NutritionSource.USER;
        i.nutritionOverride = nutrition == null || nutrition.isEmpty() ? null : nutrition;
        return i;
    }

    // 수정. servings·name은 null이면 그대로, weight는 바꾸라고 했을 때만 바꿈 (null이면 무게를 지우고 인분으로 계산)
    public void update(BigDecimal servings, boolean changeWeight, BigDecimal weight, String name) {
        if (servings != null) this.servings = servings;
        if (changeWeight) this.weight = weight;
        if (name != null) this.name = name;
    }

    // 직접 적은 음식인지 (식약처 음식을 고른 게 아님)
    public boolean isCustom() {
        return foodCode == null;
    }

    // 먹은 양 전체의 영양값 (소수 첫째 자리)
    // 직접 적은 값이 있으면 그 값을 그대로 쓰고, 안 적은 항목만 복사해 둔 공공 값 × 먹은 양으로 계산함
    public MealNutrition eatenNutrition() {
        BigDecimal ratio = eatenAmount().divide(HUNDRED);
        MealNutrition o = nutritionOverride;
        return new MealNutrition(
                pick(o == null ? null : o.kcal(), kcal, ratio),
                pick(o == null ? null : o.carbG(), carbG, ratio),
                pick(o == null ? null : o.proteinG(), proteinG, ratio),
                pick(o == null ? null : o.fatG(), fatG, ratio),
                pick(o == null ? null : o.sugarG(), sugarG, ratio));
    }

    // 먹은 양 (unit 단위). 잰 무게가 있으면 그것, 없으면 1인분 양(모르면 100) × 인분
    private BigDecimal eatenAmount() {
        if (weight != null) return weight;
        BigDecimal perServing = servingAmount != null ? servingAmount : HUNDRED;
        return perServing.multiply(servings);
    }

    private static BigDecimal pick(BigDecimal override, BigDecimal per100, BigDecimal ratio) {
        if (override != null) return override.setScale(1, RoundingMode.HALF_UP);
        if (per100 == null) return null;
        return per100.multiply(ratio).setScale(1, RoundingMode.HALF_UP);
    }
}
