package com.dangdang.domain.shop.controller;

import com.dangdang.domain.shop.dto.response.ProductOutboundResponse;
import com.dangdang.domain.shop.dto.response.ProductVisitListResponse;
import com.dangdang.domain.shop.service.ProductVisitService;
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
public class ProductVisitController {

    private final ProductVisitService productVisitService;

    @Operation(summary = "[ 사용자 | 토큰 O | 판매처로 이동 (P-06 「○○몰로 이동하기」 · M-22 「다시 가기」) ]",
            description = """
                    **Returns** url (DB에 있는 판매처 https 주소). 「판매처로 간 기록」을 남기고 주소를 돌려준다 → 프론트는 받은 url 로 이동
                    10분 안에 같은 상품을 또 누르면(동시에 눌러도) 기록은 한 줄로 합치고 시각·가격·판매처 이름만 새로. 없거나 판매 끝난 상품 404 SHOP_40401
                    """)
    @PostMapping("/api/products/{productId}/visits")
    public ResponseEntity<BaseResponse<ProductOutboundResponse>> visit(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long productId) {
        return ResponseEntity.ok(BaseResponse.success(200, "판매처 주소를 보냈어요.",
                productVisitService.visit(principal.userId(), productId)));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 판매처로 간 기록 (M-22) ]",
            description = """
                    **Parameters** cursor(앞 응답의 nextCursor) · size(1~100, 기본 20)
                    **Returns** items[id, productId, name, imageUrl, price(갔을 때), sellerName(갔을 때), visitedAt, available], nextCursor, hasNext
                    최근 것 먼저. 판매 끝난 상품도 나오고 available=false. 다른 목록 커서는 400 SHOP_40001
                    """)
    @GetMapping("/api/me/product-visits")
    public ResponseEntity<BaseResponse<ProductVisitListResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1~100이에요.")
            @Max(value = 100, message = "size는 1~100이에요.") int size) {
        return ResponseEntity.ok(BaseResponse.success(200, "판매처로 간 기록 조회에 성공했습니다.",
                productVisitService.list(principal.userId(), cursor, size)));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 판매처로 간 기록 모두 지우기 (M-22) ]", description = "**Returns** data = 지운 개수 (내 기록만). 없어도 200, 0. 같은 사람의 「판매처로 이동」과 겹치면 차례로 처리")
    @DeleteMapping("/api/me/product-visits")
    public ResponseEntity<BaseResponse<Integer>> clear(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(BaseResponse.success(200, "판매처로 간 기록을 지웠어요.",
                productVisitService.clear(principal.userId())));
    }
}
