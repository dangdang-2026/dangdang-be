package com.dangdang.domain.shop.dto.response;

import com.dangdang.domain.shop.entity.Product;
import com.dangdang.domain.shop.enums.ProductCategory;
import io.swagger.v3.oas.annotations.media.Schema;

// 상품 카드 하나 (P-01 목록 · M-21 찜한 상품 · 최근 본 상품)
public record ProductSummaryResponse(
        @Schema(example = "12") Long id,
        @Schema(example = "SNACK") ProductCategory category,
        @Schema(example = "브랜드 B") String brand,
        @Schema(example = "무가당 그릭요거트, 100g, 4개입") String name,
        @Schema(description = "용량 글자 (없으면 null)", example = "100g × 4") String sizeLabel,
        @Schema(description = "판매처 가격(원)", example = "9800") Integer price,
        @Schema(description = "대표 사진 (없으면 null)") String imageUrl,
        @Schema(description = "이번 주 산모 PICK") boolean pick,
        @Schema(description = "제휴 링크 → 「제휴」 표시") boolean affiliate,
        @Schema(description = "내가 찜했는지") boolean liked) {

    public static ProductSummaryResponse of(Product p, boolean liked) {
        return new ProductSummaryResponse(p.getId(), p.getCategory(), p.getBrand(), p.getName(), p.getSizeLabel(),
                p.getPrice(), p.mainImageUrl(), p.isPick(), p.isAffiliate(), liked);
    }
}
