package com.dangdang.domain.myfood.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;

import java.math.BigDecimal;

// 내가 입력한 1인분 영양값 (g · kcal). 안 적은 칸은 null — 0과 다르다
public record Nutrition(
        @Schema(example = "45") @DecimalMin("0") @DecimalMax("1000") BigDecimal carbG,
        @Schema(example = "12.5") @DecimalMin("0") @DecimalMax("1000") BigDecimal proteinG,
        @DecimalMin("0") @DecimalMax("1000") BigDecimal fatG,
        @DecimalMin("0") @DecimalMax("1000") BigDecimal sugarG,
        @DecimalMin("0") @DecimalMax("5000") BigDecimal kcal) {

    // 객체를 보냈으면 한 칸은 있어야 한다
    // @JsonIgnore: 안 붙이면 is…() 를 칸으로 착각해 JSONB에 "anyFilled"가 저장되고, 다시 읽을 때 500뜸
    @JsonIgnore
    @AssertTrue(message = "영양값을 하나 이상 적어 주세요.")
    public boolean isAnyFilled() {
        return carbG != null || proteinG != null || fatG != null || sugarG != null || kcal != null;
    }
}
