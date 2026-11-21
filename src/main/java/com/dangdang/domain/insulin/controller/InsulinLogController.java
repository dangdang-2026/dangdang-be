package com.dangdang.domain.insulin.controller;

import com.dangdang.domain.insulin.dto.request.InsulinLogCreateRequest;
import com.dangdang.domain.insulin.dto.response.InsulinLogPageResponse;
import com.dangdang.domain.insulin.dto.response.InsulinLogResponse;
import com.dangdang.domain.insulin.service.InsulinLogService;
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
                    **Parameters** insulinType(MEAL 식사 때 · BEDTIME 자기 전 · CUSTOM 직접 입력, 필수),
                    units(맞은 단위, 필수, 0 초과 999.9 이하, 소수 한 자리까지), takenAt(맞은 시각, 필수, 시간대 포함, 2000년 이후 ~ 지금)
                    **Returns** 저장된 기록 {insulinLogId, insulinType, units, takenAt, createdAt, updatedAt}.
                    적은 그대로 저장만 함 (권장량 계산·투여 알림 없음). 범위 밖이거나 빠진 값이 있으면 GLOBAL_40001
                    """)
    @PostMapping
    public ResponseEntity<BaseResponse<InsulinLogResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody InsulinLogCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(201, "인슐린 투여 기록 등록에 성공했습니다.",
                        insulinLogService.create(principal.userId(), request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 인슐린 투여 기록 목록 ]",
            description =
                    """
                    **Parameters** from·to(한국 날짜 yyyy-MM-dd, 필수, 둘 다 포함), page(기본 0, 0~10000), size(기본 20, 1~100)
                    **Returns** items[]{insulinLogId, insulinType, units, takenAt, createdAt, updatedAt}·page·size·hasNext.
                    맞은 시각 오래된 순, 지운 기록은 빠짐. 없으면 items=[].
                    날짜가 없거나 형식이 틀리거나 from > to, page·size가 범위 밖이면 GLOBAL_40001
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
}
