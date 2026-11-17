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

    @Column(nullable = false, length = 150)
    private String name;                     // 보이는 이름. displayName은 식품명(100자)보다 길어질 수 있어 150자 (직접 적는 이름은 요청에서 100자 제한)

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
    private MealNutrition nutritionOverride; // 직접 적은 영양값 (적을 때의 먹은 양 전체 기준)

    @Column(precision = 10, scale = 3)
    private BigDecimal overrideAmount;       // 직접 적을 때의 먹은 양. 나중에 양이 바뀌면 이 비율로 적은 값도 바뀜

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
        if (nutrition != null && !nutrition.isEmpty()) {
            i.nutritionOverride = nutrition;
            i.overrideAmount = i.eatenAmount();   // 적은 값은 지금 먹은 양 기준
        }
        return i;
    }

    // 수정. servings·name은 null이면 그대로, weight는 바꾸라고 했을 때만 바꿈 (null이면 무게를 지우고 인분으로 계산)
    // 무게가 있는 항목에서 인분만 바꾸면 무게도 같은 비율로 바꿈 (1인분 300g → 2인분이면 600g)
    //   무게가 있으면 영양값은 무게로 계산해서, 무게를 안 바꾸면 "2인분"인데 1인분 값이 나옴
    public void update(BigDecimal servings, boolean changeWeight, BigDecimal weight, String name) {
        if (changeWeight) {
            this.weight = weight;
        } else if (servings != null && this.weight != null && this.servings.signum() > 0) {
            this.weight = this.weight.multiply(servings).divide(this.servings, 1, RoundingMode.HALF_UP);
        }
        if (servings != null) this.servings = servings;
        if (name != null) this.name = name;
    }

    // 영양값 직접 입력. 적은 칸만 사용자 값으로 쓰고, 안 적은 칸은 계속 공공 값으로 계산함
    // 무게는 바꾸라고 했을 때만 바꿈 (null이면 무게를 지우고 인분으로 계산)
    // 적은 값은 무게까지 바꾼 뒤의 먹은 양 기준으로 기억함 → 적은 직후엔 적은 값 그대로 (명세: 다시 곱하지 않음)
    public void overrideNutrition(MealNutrition nutrition, boolean changeWeight, BigDecimal weight) {
        if (changeWeight) this.weight = weight;
        this.nutritionOverride = nutrition;
        this.overrideAmount = eatenAmount();
        this.nutritionSource = NutritionSource.USER;
    }

    // 공공 영양값으로 되돌리기. 직접 적은 값을 지우고, 추가할 때 복사해 둔 공공 값으로 다시 계산함
    // 먹은 양(인분·무게)은 그대로. 이미 공공 값이면 아무것도 안 바꾸고 false (여러 번 해도 같음)
    public boolean revertNutrition() {
        if (nutritionSource == NutritionSource.DB && nutritionOverride == null) {
            return false;
        }
        this.nutritionOverride = null;
        this.overrideAmount = null;
        this.nutritionSource = NutritionSource.DB;
        return true;
    }

    // 삭제 (soft delete). 줄을 지우지 않고 지운 시각만 적어 둠
    public void delete(Instant now) {
        this.deletedAt = now;
    }

    // 직접 적은 음식인지 (식약처 음식을 고른 게 아님)
    public boolean isCustom() {
        return foodCode == null;
    }

    // 먹은 양 전체의 영양값 (소수 첫째 자리)
    // 직접 적은 칸: 적은 값 × (지금 먹은 양 ÷ 적을 때 먹은 양). 적은 뒤 양을 안 바꿨으면 적은 값 그대로
    //   예) 1인분일 때 탄수화물 40g으로 적고 2인분으로 바꾸면 80g
    // 안 적은 칸: 복사해 둔 공공 값(100당) × 먹은 양 ÷ 100
    public MealNutrition eatenNutrition() {
        BigDecimal amount = eatenAmount();
        BigDecimal ratio = amount.divide(HUNDRED);
        MealNutrition o = nutritionOverride;
        return new MealNutrition(
                pick(o == null ? null : o.kcal(), kcal, amount, ratio),
                pick(o == null ? null : o.carbG(), carbG, amount, ratio),
                pick(o == null ? null : o.proteinG(), proteinG, amount, ratio),
                pick(o == null ? null : o.fatG(), fatG, amount, ratio),
                pick(o == null ? null : o.sugarG(), sugarG, amount, ratio));
    }

    // 먹은 양 (unit 단위). 잰 무게가 있으면 그것, 없으면 1인분 양(모르면 100) × 인분
    private BigDecimal eatenAmount() {
        if (weight != null) return weight;
        BigDecimal perServing = servingAmount != null ? servingAmount : HUNDRED;
        return perServing.multiply(servings);
    }

    private BigDecimal pick(BigDecimal override, BigDecimal per100, BigDecimal amount, BigDecimal ratio) {
        if (override != null) {
            if (overrideAmount == null || overrideAmount.signum() == 0) return override.setScale(1, RoundingMode.HALF_UP);
            return override.multiply(amount).divide(overrideAmount, 1, RoundingMode.HALF_UP);
        }
        if (per100 == null) return null;
        return per100.multiply(ratio).setScale(1, RoundingMode.HALF_UP);
    }
}
