package com.dangdang.domain.reading.controller;

import com.dangdang.domain.reading.dto.request.ReadingCreateRequest;
import com.dangdang.domain.reading.dto.response.ReadingResponse;
import com.dangdang.domain.reading.service.ReadingService;
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

@Tag(name = "Reading", description = "혈당 기록 API")
@RestController
@RequestMapping("/api/readings")
@RequiredArgsConstructor
public class ReadingController {

    private final ReadingService readingService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 혈당 기록 등록 ]",
            description =
                    """
                    **Parameters** value(20~600, 필수), measuredAt(잰 시각, 필수), readingType(FASTING/POST_MEAL/OTHER, 필수), slot(선택)
                    **Returns** 저장된 기록. 잰 날짜의 병원 기준이 자동으로 붙고(없으면 빈 기준), 30분 넘게 늦게 적으면 enteredLate=true. 범위 밖이면 GLOBAL_40001
                    """)
    @PostMapping
    public ResponseEntity<BaseResponse<ReadingResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody ReadingCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(201, "혈당 기록을 저장했습니다.",
                        readingService.create(principal.userId(), request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 혈당 기록 상세 ]",
            description =
                    """
                    **Parameters** readingId
                    **Returns** 기록 하나. 없거나 삭제됐거나 남의 기록이면 READING_40401
                    """)
    @GetMapping("/{readingId}")
    public ResponseEntity<BaseResponse<ReadingResponse>> get(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long readingId) {
        return ResponseEntity.ok(BaseResponse.success(200, "혈당 기록 조회에 성공했습니다.",
                readingService.get(principal.userId(), readingId)));
    }
}
