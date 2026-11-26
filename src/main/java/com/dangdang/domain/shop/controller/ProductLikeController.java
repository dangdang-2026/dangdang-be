package com.dangdang.domain.shop.controller;

import com.dangdang.domain.shop.dto.response.LikedProductListResponse;
import com.dangdang.domain.shop.service.ProductLikeService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Shop", description = "당당 상점 API (결제 없음 · 판매처 링크)")
@RestController
@RequiredArgsConstructor
public class ProductLikeController {

    private final ProductLikeService productLikeService;

    @Operation(summary = "[ 사용자 | 토큰 O | 찜하기 ]", description = "이미 찜했어도 200 (두 번·동시에 눌러도 한 줄). 없거나 판매 끝난 상품 404 SHOP_40401")
    @PutMapping("/api/products/{productId}/like")
    public ResponseEntity<BaseResponse<Void>> like(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long productId) {
        productLikeService.like(principal.userId(), productId);
        return ResponseEntity.ok(BaseResponse.success(200, "찜했어요.", null));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 찜 빼기 ]", description = "찜 안 했거나 판매 끝난 상품이어도 200")
    @DeleteMapping("/api/products/{productId}/like")
    public ResponseEntity<BaseResponse<Void>> unlike(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long productId) {
        productLikeService.unlike(principal.userId(), productId);
        return ResponseEntity.ok(BaseResponse.success(200, "찜을 뺐어요.", null));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 찜한 상품 (M-21) ]",
            description = """
                    **Parameters** cursor(앞 응답의 nextCursor) · size(1~100, 기본 20)
                    **Returns** items(상품 카드, liked=true), nextCursor, hasNext, totalCount(화면 위 「n개」 = 판매 중인 찜 전체 수)
                    최근에 찜한 것 먼저, 판매 끝난 상품은 목록·숫자 둘 다에서 빠짐. 다른 목록 커서는 400 SHOP_40001
                    """)
    @GetMapping("/api/me/liked-products")
    public ResponseEntity<BaseResponse<LikedProductListResponse>> liked(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1~100이에요.")
            @Max(value = 100, message = "size는 1~100이에요.") int size) {
        return ResponseEntity.ok(BaseResponse.success(200, "찜한 상품 조회에 성공했습니다.",
                productLikeService.liked(principal.userId(), cursor, size)));
    }
}
