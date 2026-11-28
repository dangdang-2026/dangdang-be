package com.dangdang.domain.shop.controller;

import com.dangdang.domain.shop.dto.response.ProductCardListResponse;
import com.dangdang.domain.shop.dto.response.ProductRecommendationResponse;
import com.dangdang.domain.shop.dto.response.ProductReviewPageResponse;
import com.dangdang.domain.shop.enums.ProductReviewFilter;
import com.dangdang.domain.shop.service.ProductReviewQueryService;
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

// 상점 혈당 후기 · 상점 메인 두 묶음 (be #192)
@Tag(name = "Shop", description = "당당 상점 API (결제 없음 · 판매처 링크)")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductReviewController {

    private final ProductReviewQueryService productReviewQueryService;

    @Operation(summary = "[ 사용자 | 토큰 O | 상품 혈당 후기 목록 ]",
            description = """
                    **Parameters** productId · filter(ALL·ONE_HOUR·TWO_HOUR·NO_INSULIN, 기본 ALL) · cursor(다음 페이지일 때만) · size(1~50, 기본 20)
                    **Returns** summary{collecting, reviewerCount, inTargetCount, aboveCount} · items[id, pregnancyWeek, postMealMinutes, value, inTarget, withFoods] · nextCursor · hasNext
                    상점 상품을 고른 식사의 식후 혈당 중 PRODUCT_REVIEW 동의를 켠 사람 것만 이름 없이 모은다. 목표 안 = 잴 때 그 사람 병원 식후 목표 미만 (목표가 빈 기준은 뺌)
                    summary 는 고른 필터 안의 사람 수 (한 사람은 가장 최근 식후 혈당 하나로). 5명보다 적으면 collecting=true, 숫자 null, items []
                    items 는 식후 혈당 한 번이 한 줄, 최근 잰 순. pregnancyWeek 는 잰 날 주수(모르면 null), withFoods 는 같은 식사의 식약처 음식·상점 상품 이름(최대 3개, 직접 적은 이름은 안 줌)
                    ONE_HOUR·TWO_HOUR = 잴 때 병원 기준의 식후 60·120분, NO_INSULIN = 지금 임신 정보의 관리 방법이 인슐린이 아닌 사람
                    없거나 판매 끝난 상품 404 SHOP_40401, 다른 상품·필터의 커서나 틀린 커서 400 SHOP_40001
                    """)
    @GetMapping("/{productId}/reviews")
    public ResponseEntity<BaseResponse<ProductReviewPageResponse>> reviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "ALL") ProductReviewFilter filter,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1~50이에요.")
            @Max(value = 50, message = "size는 1~50이에요.") int size) {
        return ResponseEntity.ok(BaseResponse.success(200, "상품 혈당 후기 조회에 성공했습니다.",
                productReviewQueryService.page(productId, filter, cursor, size)));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 산모들이 먹고 기록한 간식 (상점 메인) ]",
            description = """
                    **Parameters** size(1~20, 기본 10)
                    **Returns** items[상품 카드, 목록과 같은 모양] — 혈당 후기가 5명 이상 모인 판매 중 상품, 사람 많은 순 (같으면 목표 안 비율 높은 순). 없으면 []
                    """)
    @GetMapping("/reviewed")
    public ResponseEntity<BaseResponse<ProductCardListResponse>> reviewed(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size는 1~20이에요.")
            @Max(value = 20, message = "size는 1~20이에요.") int size) {
        return ResponseEntity.ok(BaseResponse.success(200, "후기가 모인 상품 조회에 성공했습니다.",
                productReviewQueryService.reviewed(principal.userId(), size)));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 내 기록 기반 저당 추천 (상점 메인) ]",
            description = """
                    **Parameters** size(1~20, 기본 10)
                    **Returns** basis{days(14), snackReadings, aboveCount} · items[product(상품 카드), reason]
                    basis = 최근 14일 내 간식 식후 혈당 수(간식 끼니이거나 상점 상품을 고른 식사)와 그중 내 병원 식후 목표보다 높았던 수. 기록이 없으면 0, 0
                    고르는 법: 간식·음료 중 목표보다 높았던 식사에 먹은 상품은 빼고, reason 에 맞는 상품만
                    REVIEWED_IN_TARGET(후기 5명 이상, 목표 안 70% 이상)을 먼저, 그다음 1개 당이 적은 순, 탄수화물 적은 순
                    reason: REVIEWED_IN_TARGET · LOW_SUGAR(1개에 당 5g 이하) · LOW_CARB(1개에 탄수화물 15g 이하). 문구는 화면에서 (「혈당 안 올려요」 같은 효능 단정 금지)
                    """)
    @GetMapping("/recommended")
    public ResponseEntity<BaseResponse<ProductRecommendationResponse>> recommended(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size는 1~20이에요.")
            @Max(value = 20, message = "size는 1~20이에요.") int size) {
        return ResponseEntity.ok(BaseResponse.success(200, "저당 추천 조회에 성공했습니다.",
                productReviewQueryService.recommended(principal.userId(), size)));
    }
}
