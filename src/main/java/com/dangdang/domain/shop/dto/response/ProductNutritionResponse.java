package com.dangdang.domain.shop.dto.response;

import com.dangdang.domain.shop.entity.Product;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

// P-02 「영양 (1개) 탄수 5g · 당 4g · 단백질 10g · 90kcal」. 모르는 값은 null (그 글자만 빼고 보여 주기)
public record ProductNutritionResponse(
        @Schema(description = "영양 기준", example = "1개") String servingLabel,
        @Schema(example = "5") BigDecimal carbG,
        @Schema(example = "4") BigDecimal sugarG,
        @Schema(example = "10") BigDecimal proteinG,
        @Schema(example = "90") BigDecimal kcal) {

    // 영양 칸이 다 비었으면 null → 프론트는 「영양」 줄을 숨김
    public static ProductNutritionResponse of(Product p) {
        if (!p.hasNutrition()) return null;
        return new ProductNutritionResponse(p.getServingLabel(), p.getCarbG(), p.getSugarG(), p.getProteinG(), p.getKcal());
    }
}
