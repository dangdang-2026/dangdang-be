package com.dangdang.domain.shop.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// 「OO님을 위한 저당 추천」 (be #192). 문구는 프론트가 쓰고, 서버는 숫자와 이유 코드만 준다 (효능 단정 금지)
public record ProductRecommendationResponse(Basis basis, List<Item> items) {

    public record Basis(
            @Schema(description = "본 기간 (일)", example = "14") int days,
            @Schema(description = "그 기간 간식 식후 혈당 수 (간식 끼니 또는 상점 상품을 고른 식사)", example = "6") int snackReadings,
            @Schema(description = "그중 내 병원 식후 목표보다 높았던 수", example = "2") int aboveCount) {
    }

    public record Item(
            @Schema(description = "상품 카드 (목록과 같은 모양)") ProductSummaryResponse product,
            @Schema(description = "고른 이유: REVIEWED_IN_TARGET(후기 5명 이상, 목표 안 70% 이상) · LOW_SUGAR(1개에 당 5g 이하) · LOW_CARB(1개에 탄수화물 15g 이하)",
                    example = "LOW_SUGAR") Reason reason) {
    }

    public enum Reason {
        REVIEWED_IN_TARGET,
        LOW_SUGAR,
        LOW_CARB
    }
}
