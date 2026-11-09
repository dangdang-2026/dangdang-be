package com.dangdang.domain.meal.controller;

import com.dangdang.domain.meal.dto.request.MealCreateRequest;
import com.dangdang.domain.meal.dto.response.MealResponse;
import com.dangdang.domain.meal.service.MealService;
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

@Tag(name = "Meal", description = "식사 기록 API")
@RestController
@RequestMapping("/api/meals")
@RequiredArgsConstructor
public class MealController {

    private final MealService mealService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 식사 시작 기록 ]",
            description =
                    """
                    **Parameters** mealSlot(BREAKFAST/LUNCH/DINNER/SNACK, 필수), startedAt(식사 시작 시각, 필수, 지금 이전), startedOffline(선택, 기본 false), note(선택, 255자)
                    **Returns** 저장된 식사. 입력이 잘못되면 GLOBAL_40001
                    """)
    @PostMapping
    public ResponseEntity<BaseResponse<MealResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody MealCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(201, "식사 기록을 저장했습니다.",
                        mealService.create(principal.userId(), request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 식사 기록 상세 ]",
            description =
                    """
                    **Parameters** mealId
                    **Returns** 식사 하나. 없거나 삭제됐거나 남의 기록이면 MEAL_40401
                    """)
    @GetMapping("/{mealId}")
    public ResponseEntity<BaseResponse<MealResponse>> get(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long mealId) {
        return ResponseEntity.ok(BaseResponse.success(200, "식사 기록 조회에 성공했습니다.",
                mealService.get(principal.userId(), mealId)));
    }
}
