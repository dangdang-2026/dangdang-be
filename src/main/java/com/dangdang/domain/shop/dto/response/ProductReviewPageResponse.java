package com.dangdang.domain.shop.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// 상품 하나의 혈당 후기 목록 (be #192). 이름·닉네임·날짜 없이 숫자만
public record ProductReviewPageResponse(
        @Schema(description = "고른 필터 안에서 센 사람 수 (한 사람은 가장 최근 식후 혈당 하나로)") Summary summary,
        @Schema(description = "후기 한 줄씩, 최근 잰 순. 5명이 안 되면 []") List<Item> items,
        @Schema(description = "다음 페이지 커서 (마지막이면 null)") String nextCursor,
        boolean hasNext) {

    public record Summary(
            @Schema(description = "true면 5명 미만이라 숫자를 안 줌") boolean collecting,
            @Schema(example = "23") Integer reviewerCount,
            @Schema(description = "목표 안 n명", example = "18") Integer inTargetCount,
            @Schema(description = "목표보다 높음 n명", example = "5") Integer aboveCount) {

        public static final Summary COLLECTING = new Summary(true, null, null, null);

        public static Summary of(int people, int inTarget) {
            return people < ProductReviewSummary.MIN_PEOPLE ? COLLECTING
                    : new Summary(false, people, inTarget, people - inTarget);
        }
    }

    public record Item(
            @Schema(example = "981") Long id,
            @Schema(description = "잰 날의 임신 주수 (예정일이 없거나 임신 기간 밖이면 null)", example = "27") Integer pregnancyWeek,
            @Schema(description = "식후 몇 분에 쟀는지 60·120 (그 사람 병원 기준)", example = "60") Integer postMealMinutes,
            @Schema(description = "식후 혈당 mg/dL", example = "128") int value,
            @Schema(description = "그 사람 병원 식후 목표 안인지 (목표 미만)") boolean inTarget,
            @Schema(description = "같은 식사에 함께 먹은 것 (식약처 음식·상점 상품 이름만, 최대 3개)") List<String> withFoods) {
    }
}
