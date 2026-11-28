package com.dangdang.domain.shop.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

// 상품 카드·상세의 혈당 후기 요약. 사람이 5명보다 적으면 숫자를 안 준다 (누군지 짐작할 수 없게)
public record ProductReviewSummary(
        @Schema(description = "true면 아직 모으는 중 (5명 미만) → 숫자 대신 「모으는 중」") boolean collecting,
        @Schema(description = "먹고 식후 혈당을 남긴 사람 수 (모으는 중이면 null)", example = "23") Integer reviewerCount,
        @Schema(description = "그중 식후 목표 안인 사람 수 (모으는 중이면 null)", example = "18") Integer inTargetCount) {

    public static final int MIN_PEOPLE = 5;
    public static final ProductReviewSummary COLLECTING = new ProductReviewSummary(true, null, null);

    public static ProductReviewSummary of(int people, int inTarget) {
        return people < MIN_PEOPLE ? COLLECTING : new ProductReviewSummary(false, people, inTarget);
    }
}
