package com.dangdang.domain.myfood.controller;

import com.dangdang.domain.myfood.dto.request.MyFoodCreateRequest;
import com.dangdang.domain.myfood.dto.request.MyFoodUpdateRequest;
import com.dangdang.domain.myfood.dto.response.MyFoodResponse;
import com.dangdang.domain.myfood.service.MyFoodService;
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

import java.util.List;

@Tag(name = "My Food", description = "내 음식 API")
@RestController
@RequestMapping("/api/me/foods")
@RequiredArgsConstructor
public class MyFoodController {

    private final MyFoodService myFoodService;

    @Operation(summary = "[ 사용자 | 토큰 O | 내 음식 목록 ]",
            description = "**Parameters** q(선택, 이름에 들어간 말) **Returns** 최근에 고친 순")
    @GetMapping
    public ResponseEntity<BaseResponse<List<MyFoodResponse>>> list(
            @AuthenticationPrincipal AuthPrincipal principal, @RequestParam(required = false) String q) {
        return ResponseEntity.ok(BaseResponse.success(200, "내 음식 목록 조회에 성공했습니다.",
                myFoodService.list(principal.userId(), q)));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 내 음식 추가 ]",
            description = """
                    **Parameters** name(필수, 100자) · similarFoodCode · usualQuantity(HALF·ONE·ONE_HALF) · nutritionOverride{carbG, proteinG, fatG, sugarG, kcal}
                    같은 이름이 있으면 409 MYFOOD_40901. 영양값 객체를 보내면 한 칸 이상, 0 이상
                    """)
    @PostMapping
    public ResponseEntity<BaseResponse<MyFoodResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody MyFoodCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(201, "내 음식 등록에 성공했습니다.",
                myFoodService.create(principal.userId(), request)));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 내 음식 고치기 (M-17) ]",
            description = "**Parameters** 바꿀 칸만. 칸을 비우려면 clear: [\"nutritionOverride\"] 처럼. 아무것도 안 보내면 400 MYFOOD_40001")
    @PatchMapping("/{myFoodId}")
    public ResponseEntity<BaseResponse<MyFoodResponse>> update(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long myFoodId,
            @Valid @RequestBody MyFoodUpdateRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "내 음식을 고쳤습니다.",
                myFoodService.update(principal.userId(), myFoodId, request)));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 내 음식 지우기 (M-18) ]", description = "지워도 지난 끼니 기록은 그대로")
    @DeleteMapping("/{myFoodId}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long myFoodId) {
        myFoodService.delete(principal.userId(), myFoodId);
        return ResponseEntity.ok(BaseResponse.success(200, "내 음식을 지웠습니다.", null));
    }
}
