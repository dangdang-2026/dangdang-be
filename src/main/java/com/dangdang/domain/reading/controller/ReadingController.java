package com.dangdang.domain.reading.controller;

import com.dangdang.domain.reading.dto.request.ReadingCreateRequest;
import com.dangdang.domain.reading.dto.request.ReadingUpdateRequest;
import com.dangdang.domain.reading.dto.response.ReadingPageResponse;
import com.dangdang.domain.reading.dto.response.ReadingResponse;
import com.dangdang.domain.reading.dto.response.ReadingStatsResponse;
import com.dangdang.domain.reading.enums.ReadingType;
import com.dangdang.domain.reading.enums.StatsPeriod;
import com.dangdang.domain.reading.service.ReadingService;
import com.dangdang.domain.reading.service.ReadingStatsService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.common.QueryParams;
import com.dangdang.global.exception.CustomException;
import com.dangdang.global.exception.GlobalErrorCode;
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
    private final ReadingStatsService readingStatsService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 혈당 기록 등록 ]",
            description =
                    """
                    **Parameters** value(20~600) 또는 meterLimit(HI·LO, 혈당계에 숫자 대신 뜬 글자) 중 하나, measuredAt(잰 시각, 필수), readingType(FASTING/POST_MEAL/OTHER, 필수), slot(선택), mealId(어떤 식사의 식후 혈당인지, 선택)
                    **Returns** 저장된 기록. 잰 날짜의 병원 기준이 자동으로 붙고(없으면 빈 기준), 30분 넘게 늦게 적으면 enteredLate=true.
                    HI는 value 600, LO는 value 20으로 같이 저장되고 meterLimit에 HI·LO가 옴.
                    판정 칸: targetValue(잴 때 목표, 없으면 null)·aboveTarget(목표 이상이면 true, 목표가 없으면 null)·belowLow(70 미만)·expectedAt(재야 할 시각, 식후만)·timing(EARLY·ON_TIME·LATE)·originalValue·originalMeterLimit·editedAt(등록 땐 null).
                    범위 밖이면 GLOBAL_40001, value와 meterLimit을 같이 보내거나 둘 다 없으면 READING_40004,
                    식후 혈당이 붙은 식사 시작보다 앞 시각이면 READING_40005, mealId가 없거나 삭제됐거나 남의 식사면 MEAL_40401
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
                    항목은 상세와 같은 칸이지만 목록은 expectedAt·timing이 늘 null (재야 할 시각을 안 구함) — 기록 화면은 /api/records 사용.
                    from이 to보다 늦으면 READING_40001, 커서를 하나만 보내면 READING_40002
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
            summary = "[ 사용자 | 토큰 O | 혈당 통계 (지난 기간과 비교) ]",
            description =
                    """
                    **Parameters** slot(FASTING·POST_MEAL, 기본 POST_MEAL), period(1W·2W·1M·3M·CUSTOM, 기본 1M),
                    from·to(한국 날짜 yyyy-MM-dd, period=CUSTOM일 때만 필수, 둘 다 포함, to는 오늘까지, 최대 300일)
                    **Returns** range(보는 기간)·compareRange(바로 앞 같은 길이 기간)·targetValue(지금 목표, 없으면 null)
                    current·previous{average, inTargetRate(0~1, 목표가 없으면 null), count, missed(알림 갔는데 안 잰 수 + 「이번 끼니는 건너뛸게요」 한 수, 알림 시각 뒤 30분 지난 것만), overDays,
                    judged(목표와 비교한 수)·inTarget(그중 안)·judgedDays·inTargetDays(날 수)·belowLow(70 미만)}
                    firstRecordDate(처음 잰 날, 공복·식후·기타 전부, 없으면 null)
                    series·previousSeries(날짜별 average·count), byMeal(식후만 끼니별 평균, 공복이면 []).
                    "목표 안"은 기록마다 잴 때 적용 중이던 병원 기준으로 셈. 비교 기간에 기록도 놓친 측정도 없으면 previous·previousSeries는 null.
                    from이 to보다 늦으면 READING_40001, 값 형식이 틀리거나 CUSTOM인데 from·to가 없거나 기간이 틀리면 GLOBAL_40001
                    """)
    @GetMapping("/stats")
    public ResponseEntity<BaseResponse<ReadingStatsResponse>> stats(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String slot,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(BaseResponse.success(200, "혈당 통계 조회에 성공했습니다.",
                readingStatsService.stats(principal.userId(), statsSlot(slot), StatsPeriod.fromCode(period),
                        QueryParams.optionalDate(from, "from"), QueryParams.optionalDate(to, "to"))));
    }

    // 통계는 공복과 식후만 (기타는 비교할 목표가 없어서). 안 보내면 식후
    private static ReadingType statsSlot(String value) {
        if (value == null || value.isBlank()) {
            return ReadingType.POST_MEAL;
        }
        if (value.equals("FASTING")) return ReadingType.FASTING;
        if (value.equals("POST_MEAL")) return ReadingType.POST_MEAL;
        throw new CustomException(GlobalErrorCode.INVALID_INPUT, "slot은 FASTING 또는 POST_MEAL로 보내 주세요.");
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 혈당 기록 상세 ]",
            description =
                    """
                    **Parameters** readingId
                    **Returns** 기록 하나 (등록 응답과 같은 칸: meterLimit·판정 칸·expectedAt·timing·originalValue·originalMeterLimit·editedAt). 없거나 삭제됐거나 남의 기록이면 READING_40401
                    """)
    @GetMapping("/{readingId}")
    public ResponseEntity<BaseResponse<ReadingResponse>> get(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long readingId) {
        return ResponseEntity.ok(BaseResponse.success(200, "혈당 기록 조회에 성공했습니다.",
                readingService.get(principal.userId(), readingId)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 혈당 기록 수정 ]",
            description =
                    """
                    **Parameters** readingId + 바꿀 칸만: value(20~600) 또는 meterLimit(HI·LO), measuredAt(지금 이전), readingType, slot, mealId
                    **Returns** 수정된 기록. 잰 시각을 바꾸면 그 날짜의 병원 기준이 다시 붙고 enteredLate도 다시 계산됨.
                    식후 → 공복·기타로 바꾸면서 mealId를 안 보내면 식사 연결이 끊김(mealId null).
                    값·HI·LO·시점·잰 때·시각을 실제로 바꿨을 때만 editedAt이 바뀜(식사만 붙이거나 같은 값이면 안 바뀜). 처음 값은 originalValue·originalMeterLimit(처음이 HI·LO였으면 그 글자). 값을 안 고쳤거나 처음 값(숫자·HI·LO까지 같게)으로 되돌리면 둘 다 null, editedAt은 남음.
                    바꿀 값이 없으면 READING_40003, value와 meterLimit을 같이 보내면 READING_40004, 고친 뒤 식후 혈당이 식사 시작보다 앞이면 READING_40005,
                    없거나 삭제됐거나 남의 기록이면 READING_40401, mealId가 내 식사가 아니면 MEAL_40401,
                    같은 기록을 다른 곳에서 동시에 먼저 고쳤으면 READING_40903(새로고침 뒤 다시)
                    """)
    @PatchMapping("/{readingId}")
    public ResponseEntity<BaseResponse<ReadingResponse>> update(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long readingId,
            @Valid @RequestBody ReadingUpdateRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "혈당 기록을 수정했습니다.",
                readingService.update(principal.userId(), readingId, request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 혈당 기록 삭제 ]",
            description =
                    """
                    **Parameters** readingId
                    **Returns** 성공 시 data 없음. 이후 상세·목록에서 안 보임. 없거나 이미 삭제됐거나 남의 기록이면 READING_40401,
                    다른 곳에서 동시에 먼저 고쳤으면 READING_40903
                    """)
    @DeleteMapping("/{readingId}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long readingId) {
        readingService.delete(principal.userId(), readingId);
        return ResponseEntity.ok(BaseResponse.success(200, "혈당 기록을 삭제했습니다.", null));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 지운 혈당 기록 되돌리기 (D-08) ]",
            description =
                    """
                    **Parameters** readingId, 본문 없음
                    **Returns** 되살린 기록 (판정·재야 할 시각 칸 포함, 상세와 같은 모양). 지운 지 5분 안에만 된다. 되돌려도 「수정됨」(editedAt)은 안 붙는다.
                    없거나 남의 기록이면 404 READING_40401, 지우지 않은 기록(두 번 누름)이면 409 READING_40901, 5분이 지났으면 409 READING_40902.
                    건강정보 동의가 없으면 403 USER_40302
                    """)
    @PostMapping("/{readingId}/restore")
    public ResponseEntity<BaseResponse<ReadingResponse>> restore(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long readingId) {
        return ResponseEntity.ok(BaseResponse.success(200, "혈당 기록을 되돌렸습니다.",
                readingService.restore(principal.userId(), readingId)));
    }
}
