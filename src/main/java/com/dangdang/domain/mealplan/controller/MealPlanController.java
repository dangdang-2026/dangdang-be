package com.dangdang.domain.mealplan.controller;

import com.dangdang.domain.mealplan.dto.request.MealPlanRequest;
import com.dangdang.domain.mealplan.dto.response.MealPlanResponse;
import com.dangdang.domain.mealplan.service.MealPlanService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Meal Plan", description = "내 식사 안내 API")
@RestController
@RequestMapping("/api/me/meal-plan")
@RequiredArgsConstructor
public class MealPlanController {

    private final MealPlanService mealPlanService;

    @Operation(summary = "[ 사용자 | 토큰 O | 내 식사 안내 보기 ]",
            description = """
                    **Returns** 안내 (없으면 data: null → 식사 시트의 「내 식사 안내 보기」 줄을 숨김).
                    받은 병원을 지웠으면 careSiteId는 null, careSiteName은 그대로 남음 (C-20)
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<MealPlanResponse>> get(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(BaseResponse.success(200, "내 식사 안내 조회에 성공했습니다.",
                mealPlanService.get(principal.userId())));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 내 식사 안내 저장 (통째로) ]",
            description = """
                    **Parameters** careSiteId · placeName(병원 목록에 없는 곳을 글로, 앞뒤 공백 지운 뒤 50자(이모지도 한 글자), 한 줄, 빈 글이면 null. 병원 번호와 같이 보내도 됨) · receivedOn · mealGuide(2000자) · snackGuide(1000자) — 끼니·간식 중 하나는 필수
                    **Returns** 저장한 안내 {id, careSiteId, careSiteName, placeName, receivedOn, mealGuide, snackGuide, updatedAt}
                    받은 표현 그대로 저장. 계산·비교 없음. 식단표 사진은 서버에 안 올림(폰 사진첩·기기 저장소)
                    """)
    @PutMapping
    public ResponseEntity<BaseResponse<MealPlanResponse>> save(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody MealPlanRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "내 식사 안내를 저장했습니다.",
                mealPlanService.save(principal.userId(), request)));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 내 식사 안내 지우기 ]")
    @DeleteMapping
    public ResponseEntity<BaseResponse<Void>> delete(@AuthenticationPrincipal AuthPrincipal principal) {
        mealPlanService.delete(principal.userId());
        return ResponseEntity.ok(BaseResponse.success(200, "내 식사 안내를 지웠습니다.", null));
    }
}
