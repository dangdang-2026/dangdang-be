package com.dangdang.domain.card.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 가로 슬라이드 한 장 (공식 지침 문장만)
// JSONB에 모르는 칸이 있어도 읽기가 깨지지 않게 (meal.entity.MealNutrition과 같게)
// eyebrow·source는 11/20 추가 — 예전에 만든 카드는 둘 다 null로 읽힌다
@JsonIgnoreProperties(ignoreUnknown = true)
public record Slide(
        @Schema(example = "식후 혈당, 언제 재나요?") @NotBlank @Size(max = 60) String title,
        @Schema(example = "병원에서 정해 준 시간(식후 1시간 또는 2시간)에 잽니다.") @NotBlank @Size(max = 300) String text,
        @Schema(description = "제목 위 작은 글 (없으면 프론트가 시리즈 이름)", example = "측정 시간")
        @Size(max = 20) String eyebrow,
        @Schema(description = "이 장의 출처 기관 (없으면 안 보임)", example = "대한당뇨병학회")
        @Size(max = 50) String source) {
}
