package com.dangdang.domain.shop.dto.response;

import com.dangdang.domain.shop.entity.Product;
import com.dangdang.domain.shop.enums.ProductCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// 상품 상세 (P-02). 판매처 주소는 안 준다 → 「○○몰로 이동하기」는 POST /api/products/{id}/visits 로 기록하고 받은 url 로 이동
public record ProductDetailResponse(
        @Schema(example = "12") Long id,
        @Schema(example = "SNACK") ProductCategory category,
        @Schema(example = "브랜드 B") String brand,
        @Schema(example = "무가당 그릭요거트, 100g, 4개입") String name,
        @Schema(description = "용량 글자 (없으면 null)", example = "100g × 4") String sizeLabel,
        @Schema(description = "판매처 가격(원)", example = "9800") Integer price,
        @Schema(description = "「100g당 2,450원」의 2,450 (용량 모르면 null → 그 글자 숨김)", example = "2450") Integer pricePer100,
        @Schema(description = "「100g당」의 단위 g · ml (용량 모르면 null)", example = "g") String contentUnit,
        @Schema(description = "판매처 배송 글자 (없으면 null)", example = "무료배송") String shippingNote,
        @Schema(description = "영양 (모르면 null → 「영양」 줄 숨김)") ProductNutritionResponse nutrition,
        @Schema(description = "사진들 (첫 번째가 대표, 없으면 [])") List<String> imageUrls,
        @Schema(example = "○○몰") String sellerName,
        @Schema(description = "이번 주 산모 PICK") boolean pick,
        @Schema(description = "제휴 링크 → 「제휴」 표시") boolean affiliate,
        @Schema(description = "내가 찜했는지") boolean liked,
        @Schema(description = "혈당 후기 요약 (5명 미만이면 collecting=true, 숫자 null)") ProductReviewSummary review) {

    public static ProductDetailResponse of(Product p, boolean liked, ProductReviewSummary review) {
        return new ProductDetailResponse(p.getId(), p.getCategory(), p.getBrand(), p.getName(), p.getSizeLabel(),
                p.getPrice(), p.pricePer100(), p.getContentUnit(), p.getShippingNote(), ProductNutritionResponse.of(p),
                p.images(), p.getSellerName(), p.isPick(), p.isAffiliate(), liked, review);
    }
}
