package com.dangdang.domain.mealplan.controller;

import com.dangdang.domain.mealplan.dto.request.MealSettingRequest;
import com.dangdang.domain.mealplan.dto.response.MealSettingResponse;
import com.dangdang.domain.mealplan.service.MealSettingService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Meal Setting", description = "식사 기록 설정 API")
@RestController
@RequestMapping("/api/me/meal-settings")
@RequiredArgsConstructor
public class MealSettingController {

    private final MealSettingService mealSettingService;

    @Operation(summary = "[ 사용자 | 토큰 O | 식사 기록 설정 보기 ]",
            description = "**Returns** portionUnit — 바꾼 적 없으면 SERVING(인분). 식사 기록 시트가 처음 보여 줄 양 칸")
    @GetMapping
    public ResponseEntity<BaseResponse<MealSettingResponse>> get(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(BaseResponse.success(200, "식사 기록 설정 조회에 성공했습니다.",
                mealSettingService.get(principal.userId())));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 식사 기록 설정 바꾸기 ]",
            description = "**Parameters** portionUnit SERVING(인분) · GRAM(그램). 저장한 기록의 양은 바뀌지 않는다")
    @PatchMapping
    public ResponseEntity<BaseResponse<MealSettingResponse>> change(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody MealSettingRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "식사 기록 설정을 저장했습니다.",
                mealSettingService.change(principal.userId(), request.portionUnit())));
    }
}
