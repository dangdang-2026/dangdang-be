package com.dangdang.domain.shop.controller;

import com.dangdang.domain.shop.dto.response.RecentProductListResponse;
import com.dangdang.domain.shop.service.RecentProductService;
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
@RequestMapping("/api/me/recent-products")
@RequiredArgsConstructor
public class RecentProductController {

    private final RecentProductService recentProductService;

    @Operation(summary = "[ 사용자 | 토큰 O | 최근 본 상품 ]",
            description = """
                    **Parameters** cursor(앞 응답의 nextCursor) · size(1~100, 기본 20)
                    **Returns** items[product(상품 카드), viewedAt], nextCursor, hasNext
                    오늘 포함 최근 7일(한국 날짜) · 판매 중인 것만 · 최근 본 것 먼저. 상품 상세(GET /api/products/{productId})를 열면 남음. 다른 목록 커서는 400 SHOP_40001
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<RecentProductListResponse>> recent(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1~100이에요.")
            @Max(value = 100, message = "size는 1~100이에요.") int size) {
        return ResponseEntity.ok(BaseResponse.success(200, "최근 본 상품 조회에 성공했습니다.",
                recentProductService.recent(principal.userId(), cursor, size)));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 최근 본 상품 하나 지우기 ]", description = "본 적 없어도 200")
    @DeleteMapping("/{productId}")
    public ResponseEntity<BaseResponse<Void>> remove(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long productId) {
        recentProductService.remove(principal.userId(), productId);
        return ResponseEntity.ok(BaseResponse.success(200, "최근 본 상품에서 지웠어요.", null));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 최근 본 상품 모두 지우기 ]", description = "7일 지난 것·판매 끝난 것까지 전부. 없어도 200")
    @DeleteMapping
    public ResponseEntity<BaseResponse<Void>> clear(@AuthenticationPrincipal AuthPrincipal principal) {
        recentProductService.clear(principal.userId());
        return ResponseEntity.ok(BaseResponse.success(200, "최근 본 상품을 모두 지웠어요.", null));
    }
}
