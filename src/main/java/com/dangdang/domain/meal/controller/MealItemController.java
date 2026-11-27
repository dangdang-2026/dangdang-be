package com.dangdang.domain.meal.controller;

import com.dangdang.domain.meal.dto.request.MealItemCreateRequest;
import com.dangdang.domain.meal.dto.request.MealItemNutritionRequest;
import com.dangdang.domain.meal.dto.request.MealItemUpdateRequest;
import com.dangdang.domain.meal.dto.response.MealItemResponse;
import com.dangdang.domain.meal.service.MealItemService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Meal Item", description = "식사 음식 항목 API")
@RestController
@RequestMapping("/api/meals/{mealId}/items")
@RequiredArgsConstructor
public class MealItemController {

    private final MealItemService mealItemService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 식사 항목 추가 ]",
            description =
                    """
                    **Parameters** mealId, foodCode(식약처 음식을 고를 때) · productId(상점 상품을 고를 때) · name(직접 적을 때, 100자) 중 하나만,
                    servings(먹은 양 인분, 기본 1, 0 초과 20 이하), weightG(직접 잰 무게, 음료는 ml, 보내면 servings 대신 씀),
                    nutrition{kcal, carbG, proteinG, fatG, sugarG}(직접 적은 음식만, 지금 먹은 양 전체 기준 — 나중에 양을 바꾸면 비율만큼 바뀜, 모르는 칸은 생략)
                    **Returns** itemId, mealId, foodCode, productId(상점 상품이 아니면 null), name, servings, weightG, unit(g·ml), nutritionSource(DB·USER),
                    nutrition(먹은 양 전체의 영양값, 모르는 값은 null), nutritionOverride(직접 적은 값만, 없으면 null), createdAt, updatedAt,
                    mealUpdatedAt(바뀐 뒤 식사 수정 시각 — 영양값 직접 입력의 expectedUpdatedAt으로 씀).
                    고른 음식은 그때의 공공 영양값을 복사해 둬서 음식 데이터가 바뀌어도 이 기록은 그대로임. name은 음식의 보이는 이름(displayName).
                    식사가 없거나 남의 것이면 MEAL_40401, 음식 코드가 없으면 FOOD_40401,
                    foodCode·productId·name 중 하나만 보내지 않으면 MEAL_40004, foodCode·productId와 nutrition을 같이 보내면 MEAL_40005, 값 범위가 틀리면 GLOBAL_40001
                    상점 상품: 이름은 「브랜드 상품명」, 영양값은 상품 1개 값 × servings(개수, unitLabel 「개」). weightG를 보내면 MEAL_40009, 없거나 판매 끝난 상품이면 SHOP_40401.
                    PRODUCT_REVIEW 동의를 켠 사람이면 이 식사의 식후 혈당이 상품 혈당 후기에 이름 없이 모인다
                    """)
    @PostMapping
    public ResponseEntity<BaseResponse<MealItemResponse>> add(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long mealId,
            @Valid @RequestBody MealItemCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(201, "식사 항목을 추가했습니다.",
                        mealItemService.add(principal.userId(), mealId, request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 식사 항목 수정 ]",
            description =
                    """
                    **Parameters** mealId, itemId, 보낸 칸만 바뀜: servings(인분, 0 초과 20 이하),
                    weightG(직접 잰 무게. null을 보내면 무게를 지우고 servings로 계산), name(직접 적은 음식만, 100자)
                    **Returns** 바뀐 항목 (추가 응답과 같은 모양). 음식 자체(foodCode)는 못 바꿈 — 다른 음식이면 지우고 새로 추가.
                    아무 칸도 안 보내면 MEAL_40003, 고른 음식의 이름을 바꾸려 하면 MEAL_40006,
                    식사가 없거나 남의 것이면 MEAL_40401, 항목이 없거나 지워졌거나 다른 식사의 항목이면 MEAL_40402, 값 범위가 틀리면 GLOBAL_40001
                    """)
    @PatchMapping("/{itemId}")
    public ResponseEntity<BaseResponse<MealItemResponse>> update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long mealId,
            @PathVariable Long itemId,
            @Valid @RequestBody MealItemUpdateRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "식사 항목을 수정했습니다.",
                mealItemService.update(principal.userId(), mealId, itemId, request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 식사 항목 영양값 직접 입력 ]",
            description =
                    """
                    **Parameters** mealId, itemId, nutritionOverride{kcal, carbG, proteinG, fatG, sugarG}(필수, 최소 1칸, 0 이상, 소수 첫째 자리, 이번에 적는 먹은 양 전체 기준 — 나중에 양을 바꾸면 비율만큼 바뀜),
                    weightG(선택, 영양성분표 기준 무게. null을 보내면 무게를 지움, 안 보내면 그대로),
                    expectedUpdatedAt(필수, 화면을 열 때 받은 식사 updatedAt 또는 항목 응답의 mealUpdatedAt)
                    **Returns** 바뀐 항목. nutritionSource=USER, nutrition은 적은 칸은 그 값, 안 적은 칸은 공공 값 × 먹은 양.
                    nutritionOverride는 직접 적은 값만. 공공 음식 데이터(foods)는 바뀌지 않음.
                    영양값을 하나도 안 적으면 MEAL_40007, 그 사이 다른 곳에서 식사가 바뀌었으면 MEAL_40901(409, 새로 불러와서 다시),
                    식사가 없거나 남의 것이면 MEAL_40401, 항목이 없거나 지워졌으면 MEAL_40402, 값 범위가 틀리면 GLOBAL_40001
                    """)
    @PostMapping("/{itemId}/nutrition")
    public ResponseEntity<BaseResponse<MealItemResponse>> overrideNutrition(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long mealId,
            @PathVariable Long itemId,
            @Valid @RequestBody MealItemNutritionRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "식사항목 영양값 직접 입력에 성공했습니다.",
                mealItemService.overrideNutrition(principal.userId(), mealId, itemId, request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 식사 항목 공공 영양값 되돌리기 ]",
            description =
                    """
                    **Parameters** mealId, itemId (본문 없음)
                    **Returns** 바뀐 항목. nutritionSource=DB, nutritionOverride=null, 먹은 양(servings·weightG)은 그대로.
                    영양값은 항목을 추가할 때 복사해 둔 공공 값 × 먹은 양으로 다시 계산 (지금 음식 데이터를 다시 읽지 않음).
                    이미 공공 값이어도 200. 직접 적은 음식이면 MEAL_40902(409),
                    식사가 없거나 남의 것이면 MEAL_40401, 항목이 없거나 지워졌으면 MEAL_40402
                    """)
    @DeleteMapping("/{itemId}/nutrition")
    public ResponseEntity<BaseResponse<MealItemResponse>> revertNutrition(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long mealId,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(BaseResponse.success(200, "식사항목 공공 영양값 복귀에 성공했습니다.",
                mealItemService.revertNutrition(principal.userId(), mealId, itemId)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 식사 항목 삭제 ]",
            description =
                    """
                    **Parameters** mealId, itemId
                    **Returns** 성공 시 data 없음. 식사 수정 시각(updatedAt)도 바뀜.
                    식사가 없거나 남의 것이면 MEAL_40401, 항목이 없거나 이미 지워졌거나 다른 식사의 항목이면 MEAL_40402
                    """)
    @DeleteMapping("/{itemId}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long mealId,
            @PathVariable Long itemId) {
        mealItemService.delete(principal.userId(), mealId, itemId);
        return ResponseEntity.ok(BaseResponse.success(200, "식사 항목을 삭제했습니다.", null));
    }
}
