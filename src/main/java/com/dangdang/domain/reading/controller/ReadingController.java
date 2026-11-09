package com.dangdang.domain.reading.controller;

import com.dangdang.domain.reading.dto.request.ReadingCreateRequest;
import com.dangdang.domain.reading.dto.response.ReadingPageResponse;
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

import java.time.Instant;
import java.time.LocalDate;

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
            summary = "[ 사용자 | 토큰 O | 혈당 기록 목록 ]",
            description =
                    """
                    **Parameters** from·to(한국 날짜 yyyy-MM-dd, 필수, 둘 다 포함), size(기본 20, 1~100), cursorAt·cursorId(다음 페이지일 때만, 둘 다 같이)
                    **Returns** 잰 시각 최신순 기록 + hasNext. 다음 페이지는 받은 nextCursorAt·nextCursorId를 cursorAt·cursorId로 그대로 보냄.
                    from이 to보다 늦거나 커서를 하나만 보내면 GLOBAL_40001
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<ReadingPageResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) Instant cursorAt,   // 첫 페이지면 안 보냄
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(BaseResponse.success(200, "혈당 기록 목록 조회에 성공했습니다.",
                readingService.list(principal.userId(), from, to, cursorAt, cursorId, size)));
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
