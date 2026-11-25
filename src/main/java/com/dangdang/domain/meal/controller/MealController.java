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
                    **Returns** 식사 시작 시각 최신순(각 식사에 items 포함) + hasNext. 다음 페이지는 받은 nextCursorAt·nextCursorId를 cursorAt·cursorId로 그대로 보냄.
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
                    **Returns** 식사 하나 + items(먹은 음식 항목, 추가한 순서, 없으면 []). 없거나 삭제됐거나 남의 기록이면 MEAL_40401
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
                    바꿀 값이 없으면 MEAL_40003, 이 식사에 붙은 식후 혈당보다 뒤 시각으로 startedAt을 옮기면 MEAL_40008(같은 시각은 됨),
                    없거나 삭제됐거나 남의 기록이면 MEAL_40401
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

    @Operation(
            summary = "[ 사용자 | 토큰 O | 지운 식사 기록 되돌리기 (D-08) ]",
            description =
                    """
                    **Parameters** mealId, 본문 없음
                    **Returns** 되살린 식사(음식 항목 포함). 지운 지 5분 안에만 된다. 지울 때 취소된 식후 알림은 시각이 아직 안 지났고 건너뛴 식사가 아니면 다시 예약.
                    없거나 남의 식사면 404 MEAL_40401, 지우지 않은 식사(두 번 누름)면 409 MEAL_40903, 5분이 지났으면 409 MEAL_40904.
                    건강정보 동의가 없으면 403 USER_40302
                    """)
    @PostMapping("/{mealId}/restore")
    public ResponseEntity<BaseResponse<MealResponse>> restore(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long mealId) {
        return ResponseEntity.ok(BaseResponse.success(200, "식사 기록을 되돌렸습니다.",
                mealService.restore(principal.userId(), mealId)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 이번 끼니 식후 측정 건너뛰기 (H-05) ]",
            description =
                    """
                    **Parameters** mealId, 본문 없음
                    **Returns** 식사 (measureSkippedAt 채워짐). 이 식사의 아직 안 끝난 측정 알림(예약·보냄·열어 봄)과 기기가 없거나 알림을 꺼서 못 보낸 알림(SKIPPED)은 DISMISSED → 재알림 안 감.
                    건너뛴 측정도 통계·진료 리포트의 「놓친 측정」에 센다 — 측정 알림이 있는 식사만 들어감
                    (간식·병원 기준 없음·식사를 늦게 적어 알림 시각이 이미 지난 식사는 알림이 없어서 빠짐). 그 뒤 혈당을 적으면 알림은 DONE으로 붙는다.
                    시작 시각을 고치거나 지웠다 되돌려도 알림을 다시 걸지 않는다. 두 번 눌러도 200(처음 시각 그대로).
                    지우지 않은 식후 혈당이 이미 붙은 끼니면 409 MEAL_40905. 없거나 삭제됐거나 남의 기록이면 404 MEAL_40401.
                    건강정보 동의가 없으면 403 USER_40302
                    """)
    @PostMapping("/{mealId}/measure-skip")
    public ResponseEntity<BaseResponse<MealResponse>> skipMeasure(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long mealId) {
        return ResponseEntity.ok(BaseResponse.success(200, "이번 끼니 측정을 건너뛰었어요.",
                mealService.skipMeasure(principal.userId(), mealId)));
    }
}
