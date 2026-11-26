package com.dangdang.domain.insulin.controller;

import com.dangdang.domain.insulin.dto.request.InsulinLogCreateRequest;
import com.dangdang.domain.insulin.dto.request.InsulinLogUpdateRequest;
import com.dangdang.domain.insulin.dto.response.InsulinLogPageResponse;
import com.dangdang.domain.insulin.dto.response.InsulinLogResponse;
import com.dangdang.domain.insulin.enums.InsulinType;
import com.dangdang.domain.insulin.service.InsulinLogService;
import com.dangdang.domain.meal.enums.MealSlot;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.common.QueryParams;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Insulin", description = "인슐린 투여 기록 API")
@RestController
@RequestMapping("/api/insulin-logs")
@RequiredArgsConstructor
public class InsulinLogController {

    private final InsulinLogService insulinLogService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 인슐린 투여 기록 등록 ]",
            description =
                    """
                    **Parameters** insulinType(MEAL 식사 때 · BEDTIME 자기 전, 필수), mealSlot(MEAL일 때만 필수: BREAKFAST·LUNCH·DINNER),
                    units(맞은 단위, 필수, 0 초과 999.9 이하, 소수 한 자리까지), takenAt(맞은 시각, 필수, 시간대 포함, 2000년 이후 ~ 지금)
                    **Returns** 저장된 기록 {insulinLogId, insulinType, mealSlot, units, takenAt, editedAt, createdAt, updatedAt}.
                    적은 그대로 저장만 함 (권장량 계산·투여 알림 없음). 범위 밖이거나 빠진 값이 있으면 GLOBAL_40001,
                    MEAL인데 끼니가 없으면 INSULIN_40003, BEDTIME에 끼니를 보내면 INSULIN_40004, 끼니가 SNACK이면 INSULIN_40005
                    """)
    @PostMapping
    public ResponseEntity<BaseResponse<InsulinLogResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody InsulinLogCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(201, "인슐린 투여 기록 등록에 성공했습니다.",
                        insulinLogService.create(principal.userId(), request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 인슐린 지난 기록 불러오기 ]",
            description =
                    """
                    **Parameters** insulinType(MEAL·BEDTIME, 필수), mealSlot(MEAL이면 필수: BREAKFAST·LUNCH·DINNER, BEDTIME이면 안 보냄)
                    **Returns** 같은 종류(식사 때면 같은 끼니까지) 중 맞은 시각이 가장 늦은 기록 1건 (등록 응답과 같은 모양, 지운 기록 빼고).
                    없으면 data 없음 (200). E-02 「지난 기록 · 어제 저녁 식사 때 6단위 불러오기」.
                    insulinType이 없거나 틀리면 GLOBAL_40001, MEAL인데 끼니가 없으면 INSULIN_40003,
                    BEDTIME에 끼니를 보내면 INSULIN_40004, 끼니가 SNACK이면 INSULIN_40005
                    """)
    @GetMapping("/latest")
    public ResponseEntity<BaseResponse<InsulinLogResponse>> latest(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam InsulinType insulinType,
            @RequestParam(required = false) MealSlot mealSlot) {
        return ResponseEntity.ok(BaseResponse.success(200, "인슐린 지난 기록 조회에 성공했습니다.",
                insulinLogService.latest(principal.userId(), insulinType, mealSlot)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 인슐린 투여 기록 목록 ]",
            description =
                    """
                    **Parameters** from·to(한국 날짜 yyyy-MM-dd, 필수, 둘 다 포함), page(기본 0, 0~10000), size(기본 20, 1~100)
                    **Returns** items[]{insulinLogId, insulinType, mealSlot, units, takenAt, editedAt, createdAt, updatedAt}·page·size·hasNext.
                    맞은 시각 오래된 순, 지운 기록은 빠짐. 없으면 items=[].
                    날짜가 없거나 형식이 틀리거나 page·size가 범위 밖이면 GLOBAL_40001, from이 to보다 늦으면 INSULIN_40001
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<InsulinLogPageResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page는 0~10000이에요.")
            @Max(value = 10000, message = "page는 0~10000이에요.") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1~100이에요.")
            @Max(value = 100, message = "size는 1~100이에요.") int size) {
        return ResponseEntity.ok(BaseResponse.success(200, "인슐린 투여 기록 목록 조회에 성공했습니다.",
                insulinLogService.list(principal.userId(),
                        QueryParams.requiredDate(from, "from"), QueryParams.requiredDate(to, "to"), page, size)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 인슐린 투여 기록 수정 ]",
            description =
                    """
                    **Parameters** insulinLogId + 바꿀 칸만: insulinType, mealSlot, units(0 초과 999.9 이하, 소수 한 자리), takenAt(2000년 이후 ~ 지금),
                    expectedUpdatedAt(선택, 화면을 열 때 받은 이 기록의 updatedAt. 안 보내면 확인 안 함)
                    안 보내거나 null을 보낸 칸은 그대로 둠 (비우기 없음). 종류를 BEDTIME으로 바꾸면 mealSlot은 서버가 지움
                    **Returns** 수정된 기록. 값이 실제로 바뀌면 editedAt이 찍힘. 바꿀 값이 없으면 INSULIN_40002, 범위 밖이면 GLOBAL_40001,
                    MEAL인데 끼니가 없으면(BEDTIME → MEAL로 바꾸며 끼니를 안 보냄 등) INSULIN_40003, BEDTIME에 끼니를 보내면 INSULIN_40004,
                    끼니가 SNACK이면 INSULIN_40005, 없거나 삭제됐거나 남의 기록이면 INSULIN_40401,
                    expectedUpdatedAt이 지금 기록과 다르면(다른 기기에서 먼저 고침) INSULIN_40901
                    """)
    @PatchMapping("/{insulinLogId}")
    public ResponseEntity<BaseResponse<InsulinLogResponse>> update(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long insulinLogId,
            @Valid @RequestBody InsulinLogUpdateRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "인슐린 투여 기록 수정에 성공했습니다.",
                insulinLogService.update(principal.userId(), insulinLogId, request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 인슐린 투여 기록 삭제 ]",
            description =
                    """
                    **Parameters** insulinLogId
                    **Returns** 성공 시 data 없음. 이후 목록·지난 기록·오늘 화면에서 안 보임 (줄은 남기고 지운 시각만 적음).
                    없거나 이미 삭제됐거나 남의 기록이면 INSULIN_40401
                    """)
    @DeleteMapping("/{insulinLogId}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long insulinLogId) {
        insulinLogService.delete(principal.userId(), insulinLogId);
        return ResponseEntity.ok(BaseResponse.success(200, "인슐린 투여 기록 삭제에 성공했습니다.", null));
    }
}
