package com.dangdang.domain.shop.dto.response;

import com.dangdang.domain.shop.entity.Product;
import com.dangdang.domain.shop.entity.ProductVisit;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

// 판매처로 간 기록 하나 (M-22). 가격·판매처는 갔을 때 값
public record ProductVisitResponse(
        @Schema(example = "301") Long id,
        @Schema(example = "12") Long productId,
        @Schema(example = "무가당 그릭요거트, 100g, 4개입") String name,
        @Schema(description = "대표 사진 (없으면 null)") String imageUrl,
        @Schema(description = "갔을 때 가격", example = "9800") Integer price,
        @Schema(description = "갔을 때 판매처", example = "○○몰") String sellerName,
        @Schema(description = "간 시각 (UTC). 프론트가 한국 날짜로 묶음", example = "2026-11-16T12:12:00Z") Instant visitedAt,
        @Schema(description = "지금도 판매 중인지 (false 면 「다시 가기」 대신 「판매 끝남」)") boolean available) {

    public static ProductVisitResponse from(ProductVisit v) {
        Product p = v.getProduct();
        return new ProductVisitResponse(v.getId(), p.getId(), p.getName(), p.mainImageUrl(), v.getPrice(),
                v.getSellerName(), v.getVisitedAt(), p.isActive());
    }
}
