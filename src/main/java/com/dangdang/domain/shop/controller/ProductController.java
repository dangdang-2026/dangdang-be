package com.dangdang.domain.shop.controller;

import com.dangdang.domain.shop.dto.response.ProductDetailResponse;
import com.dangdang.domain.shop.dto.response.ProductPageResponse;
import com.dangdang.domain.shop.enums.ProductCategory;
import com.dangdang.domain.shop.service.ProductService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Shop", description = "당당 상점 API (결제 없음 · 판매처 링크)")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "[ 사용자 | 토큰 O | 상점 상품 목록 (P-01) ]",
            description = """
                    **Parameters** category(SNACK·LUNCHBOX·DRINK·MEAL_SWAP, 없으면 전체) · pick(true면 산모 PICK만) · q(이름·브랜드 검색, 100자까지, % _ 는 글자 그대로) · page(0~10000, 기본 0) · size(1~50, 기본 20)
                    **Returns** items[id, category, brand, name, sizeLabel, price, imageUrl, pick, affiliate, liked, review{collecting, reviewerCount, inTargetCount}], page, size, hasNext
                    review = 혈당 후기 요약 (5명 미만이면 collecting=true, 숫자 null). 찜한 상품·최근 본 상품 카드도 같은 모양
                    판매 중인 상품만, 운영자가 정한 순서. 잘못된 category·범위 밖 값은 400 GLOBAL_40001
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<ProductPageResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) ProductCategory category,
            @RequestParam(defaultValue = "false") boolean pick,
            @RequestParam(required = false) @Size(max = 100, message = "검색어는 100자까지예요.") String q,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page는 0~10000이에요.")
            @Max(value = 10000, message = "page는 0~10000이에요.") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1~50이에요.")
            @Max(value = 50, message = "size는 1~50이에요.") int size) {
        return ResponseEntity.ok(BaseResponse.success(200, "상품 목록 조회에 성공했습니다.",
                productService.list(principal.userId(), category, pick, q, page, size)));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 상품 상세 (P-02) ]",
            description = """
                    **Returns** id, category, brand, name, sizeLabel, price, pricePer100, contentUnit, shippingNote, nutrition[servingLabel, carbG, sugarG, proteinG, kcal], imageUrls, sellerName, pick, affiliate, liked, review{collecting, reviewerCount, inTargetCount}
                    pricePer100·contentUnit 은 용량을 모르면 null, shippingNote 는 없으면 null, nutrition 은 영양을 모르면 null
                    판매처 주소는 안 줌 (「○○몰로 이동하기」 = POST /api/products/{productId}/visits)
                    화면을 실제로 열 때만 불러 주세요 (미리 불러오기 금지, 열면 최근 본 상품에 남음). 같은 상품은 한 줄, 시각만 새로
                    없거나 판매 끝난 상품 404 SHOP_40401
                    """)
    @GetMapping("/{productId}")
    public ResponseEntity<BaseResponse<ProductDetailResponse>> detail(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long productId) {
        return ResponseEntity.ok(BaseResponse.success(200, "상품 상세 조회에 성공했습니다.",
                productService.detail(principal.userId(), productId)));
    }
}
