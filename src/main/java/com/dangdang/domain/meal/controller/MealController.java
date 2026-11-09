package com.dangdang.domain.meal.controller;

import com.dangdang.domain.meal.dto.request.MealCreateRequest;
import com.dangdang.domain.meal.dto.request.MealUpdateRequest;
import com.dangdang.domain.meal.dto.response.MealPageResponse;
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

import java.time.Instant;
import java.time.LocalDate;

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
            summary = "[ 사용자 | 토큰 O | 식사 기록 목록 ]",
            description =
                    """
                    **Parameters** from·to(한국 날짜 yyyy-MM-dd, 필수, 둘 다 포함), size(기본 20, 1~100), cursorAt·cursorId(다음 페이지일 때만, 둘 다 같이)
                    **Returns** 식사 시작 시각 최신순 + hasNext. 다음 페이지는 받은 nextCursorAt·nextCursorId를 cursorAt·cursorId로 그대로 보냄.
                    from이 to보다 늦으면 MEAL_40001, 커서를 하나만 보내면 MEAL_40002
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<MealPageResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) Instant cursorAt,   // 첫 페이지면 안 보냄
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(BaseResponse.success(200, "식사 기록 목록 조회에 성공했습니다.",
                mealService.list(principal.userId(), from, to, cursorAt, cursorId, size)));
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

    @Operation(
            summary = "[ 사용자 | 토큰 O | 식사 기록 수정 ]",
            description =
                    """
                    **Parameters** mealId + 바꿀 칸만: mealSlot, startedAt(지금 이전), note(255자, 비우려면 "")
                    **Returns** 수정된 식사. startedOffline은 바꿀 수 없음.
                    바꿀 값이 없으면 MEAL_40003, 없거나 삭제됐거나 남의 기록이면 MEAL_40401
                    """)
    @PatchMapping("/{mealId}")
    public ResponseEntity<BaseResponse<MealResponse>> update(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long mealId,
            @Valid @RequestBody MealUpdateRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "식사 기록을 수정했습니다.",
                mealService.update(principal.userId(), mealId, request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 식사 기록 삭제 ]",
            description =
                    """
                    **Parameters** mealId
                    **Returns** 성공 시 data 없음. 이후 상세·목록에서 안 보임. 없거나 이미 삭제됐거나 남의 기록이면 MEAL_40401
                    """)
    @DeleteMapping("/{mealId}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long mealId) {
        mealService.delete(principal.userId(), mealId);
        return ResponseEntity.ok(BaseResponse.success(200, "식사 기록을 삭제했습니다.", null));
    }
}
