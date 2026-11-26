package com.dangdang.domain.shop.controller;

import com.dangdang.domain.shop.dto.response.ShoppingSummaryResponse;
import com.dangdang.domain.shop.service.ShoppingSummaryService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Shop", description = "당당 상점 API (결제 없음 · 판매처 링크)")
@RestController
@RequiredArgsConstructor
public class ShoppingSummaryController {

    private final ShoppingSummaryService shoppingSummaryService;

    @Operation(summary = "[ 사용자 | 토큰 O | 마이 › 쇼핑 메뉴 숫자 (M-01 v2) ]",
            description = "**Returns** likedCount(찜한 상품 수 = 찜한 상품 totalCount) · recentCount(오늘 포함 최근 7일(한국 날짜) 본 상품 수). 둘 다 판매 중인 상품만 셈")
    @GetMapping("/api/me/shopping-summary")
    public ResponseEntity<BaseResponse<ShoppingSummaryResponse>> summary(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(BaseResponse.success(200, "쇼핑 요약 조회에 성공했습니다.",
                shoppingSummaryService.summary(principal.userId())));
    }
}
