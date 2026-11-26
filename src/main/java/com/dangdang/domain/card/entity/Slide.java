package com.dangdang.domain.card.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

// 가로 슬라이드 한 장. 글로 쓴 장(text)이나 만든 이미지를 올린 장(imageUrl + alt) 둘 중 하나
// JSONB에 모르는 칸이 있어도 읽기가 깨지지 않게 (meal.entity.MealNutrition과 같게)
// eyebrow·source는 11/20, imageUrl·alt는 11/26 추가 — 예전에 만든 카드는 null로 읽힌다
@JsonIgnoreProperties(ignoreUnknown = true)
public record Slide(
        @Schema(description = "장 제목. 이미지 장이면 목록과 처리 기록에만 쓴다", example = "식후 혈당, 언제 재나요?")
        @NotBlank @Size(max = 60) String title,
        @Schema(description = "본문. 이미지 장이면 비워도 된다", example = "병원에서 정해 준 시간(식후 1시간 또는 2시간)에 잽니다.")
        @Size(max = 300) String text,
        @Schema(description = "제목 위 작은 글 (없으면 프론트가 시리즈 이름)", example = "측정 시간")
        @Size(max = 20) String eyebrow,
        @Schema(description = "이 장의 출처 기관 (없으면 안 보임)", example = "대한당뇨병학회")
        @Size(max = 50) String source,
        @Schema(description = "이미지 장의 주소 (POST /api/admin/card-images 가 돌려준 url). 있으면 앱은 글 대신 이미지를 보여 준다",
                example = "https://dangdang-cards.s3.ap-northeast-2.amazonaws.com/cards/3f2a.png")
        @Size(max = 500) @Pattern(regexp = "https://.*", message = "이미지는 https 주소만 돼요.") String imageUrl,
        @Schema(description = "이미지 속 글을 그대로 옮긴 대체 텍스트. 이미지 장이면 필수 (화면 읽기 기능이 읽는다)",
                example = "식후 혈당은 병원에서 정해 준 시간에 잽니다. 식후 1시간 또는 2시간이에요.")
        @Size(max = 500) String alt) {

    // 글 장은 본문이, 이미지 장은 대체 텍스트가 있어야 한다
    @JsonIgnore
    @AssertTrue(message = "글 장은 본문을, 이미지 장은 대체 텍스트를 적어 주세요.")
    public boolean isTextOrImage() {
        return StringUtils.hasText(imageUrl) ? StringUtils.hasText(alt) : StringUtils.hasText(text);
    }
}
