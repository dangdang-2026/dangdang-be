package com.dangdang.domain.record.controller;

import com.dangdang.domain.record.dto.response.CalendarResponse;
import com.dangdang.domain.record.dto.response.RecordsResponse;
import com.dangdang.domain.record.service.RecordService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.common.QueryParams;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Record", description = "기록 탭 조회 API")
@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class RecordController {

    private final RecordService recordService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 일별·주간 기록 통합 조회 ]",
            description =
                    """
                    **Parameters** from·to(한국 날짜 yyyy-MM-dd, 필수, 둘 다 포함, 최대 31일)
                    **Returns** 날짜별 days[]. 기록 없는 날도 events=[]로 들어감. events는 시각 오래된 순이고
                    type=READING이면 reading, type=MEAL이면 meal 칸만 들어감. modified는 저장 후 고친 적이 있는지.
                    meal.items는 먹은 음식 목록(추가한 순서, 지운 항목 빼고, 없으면 []). 날짜 형식이 틀리거나 from > to 거나 31일 넘으면 GLOBAL_40001
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<RecordsResponse>> records(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(BaseResponse.success(200, "일별·주간 기록 통합 조회에 성공했습니다.",
                recordService.records(principal.userId(),
                        QueryParams.requiredDate(from, "from"),
                        QueryParams.requiredDate(to, "to"))));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 기록 달력 월별 조회 ]",
            description =
                    """
                    **Parameters** month(한국 기준 달 yyyy-MM, 필수)
                    **Returns** 그 달 모든 날짜의 status(RECORDED·EMPTY·TODAY·FUTURE·BEFORE_SIGNUP)와 visit(그날 진료 일정이 있으면 true, 날짜 미정·지운 일정은 안 셈),
                    firstRecordDate(처음 기록한 날, 없으면 null), hasPrevMonth·hasNextMonth. month 형식이 틀리면 GLOBAL_40001
                    """)
    @GetMapping("/calendar")
    public ResponseEntity<BaseResponse<CalendarResponse>> calendar(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(BaseResponse.success(200, "기록 달력 조회에 성공했습니다.",
                recordService.calendar(principal.userId(), QueryParams.requiredMonth(month, "month"))));
    }
}
