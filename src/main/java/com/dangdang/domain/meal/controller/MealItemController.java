package com.dangdang.domain.meal.controller;

import com.dangdang.domain.meal.dto.request.MealItemCreateRequest;
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
                    **Parameters** mealId, foodCode(식약처 음식을 고를 때) 또는 name(직접 적을 때, 100자) 중 하나만,
                    servings(먹은 양 인분, 기본 1, 0 초과 20 이하), weightG(직접 잰 무게, 음료는 ml, 보내면 servings 대신 씀),
                    nutrition{kcal, carbG, proteinG, fatG, sugarG}(직접 적은 음식만, 먹은 양 전체 기준, 모르는 칸은 생략)
                    **Returns** itemId, mealId, foodCode, name, servings, weightG, unit(g·ml), nutritionSource(DB·USER),
                    nutrition(먹은 양 전체의 영양값, 모르는 값은 null), createdAt, updatedAt.
                    고른 음식은 그때의 공공 영양값을 복사해 둬서 음식 데이터가 바뀌어도 이 기록은 그대로임. name은 음식의 보이는 이름(displayName).
                    식사가 없거나 남의 것이면 MEAL_40401, 음식 코드가 없으면 FOOD_40401,
                    foodCode·name을 둘 다 보내거나 둘 다 안 보내면 MEAL_40004, foodCode와 nutrition을 같이 보내면 MEAL_40005, 값 범위가 틀리면 GLOBAL_40001
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
}
