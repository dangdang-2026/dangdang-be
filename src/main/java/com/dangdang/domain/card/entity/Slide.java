package com.dangdang.domain.card.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 가로 슬라이드 한 장 (공식 지침 문장만)
// JSONB에 모르는 칸이 있어도 읽기가 깨지지 않게 (meal.entity.MealNutrition과 같게)
@JsonIgnoreProperties(ignoreUnknown = true)
public record Slide(
        @Schema(example = "식후 혈당, 언제 재나요?") @NotBlank @Size(max = 60) String title,
        @Schema(example = "병원에서 정해 준 시간(식후 1시간 또는 2시간)에 잽니다.") @NotBlank @Size(max = 300) String text) {
}
