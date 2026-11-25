package com.dangdang.domain.record.controller;

import com.dangdang.domain.record.dto.response.CalendarResponse;
import com.dangdang.domain.record.dto.response.RecordDeleteResponse;
import com.dangdang.domain.record.dto.response.RecordsResponse;
import com.dangdang.domain.record.service.RecordDeleteService;
import com.dangdang.domain.record.service.RecordService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.common.QueryParams;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Record", description = "기록 탭 조회·기간 지우기 API")
@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class RecordController {

    private final RecordService recordService;
    private final RecordDeleteService recordDeleteService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 일별·주간 기록 통합 조회 ]",
            description =
                    """
                    **Parameters** from·to(한국 날짜 yyyy-MM-dd, 필수, 둘 다 포함, 최대 31일)
                    **Returns** 날짜별 days[]. 기록 없는 날도 events=[]로 들어감. events는 시각 오래된 순이고
                    type=READING이면 reading, type=MEAL이면 meal, type=INSULIN이면 insulin{insulinType, mealSlot, units} 칸만 들어감.
                    같은 시각이면 MEAL → READING → INSULIN 순. modified는 사용자가 저장 후 고친 적이 있는지
                    (혈당은 값·시점·시각을 고쳤을 때만, 지우기·되돌리기·식사만 붙이기는 안 셈.
                    인슐린은 종류·끼니·단위·시각이 실제로 바뀌었을 때만 (InsulinLog.update)).
                    고친 시각(editedAt)을 남기기 전에 고친 혈당은 modified=false·originalValue=null로 나옴 (예전 기록은 식사만 붙인 것과 진짜 고친 것을 가릴 수 없어서 비워 둠).
                    reading은 meterLimit(HI·LO)·targetValue·aboveTarget·belowLow(70 미만)·expectedAt(재야 할 시각)·timing·originalValue(처음 값)·originalMeterLimit(처음이 HI·LO였으면 그 글자)·editedAt도 줌.
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
                    firstRecordDate(혈당·식사·인슐린 중 처음 기록한 날, 없으면 null), hasPrevMonth·hasNextMonth. 인슐린만 적은 날도 RECORDED. month 형식이 틀리면 GLOBAL_40001
                    """)
    @GetMapping("/calendar")
    public ResponseEntity<BaseResponse<CalendarResponse>> calendar(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(BaseResponse.success(200, "기록 달력 조회에 성공했습니다.",
                recordService.calendar(principal.userId(), QueryParams.requiredMonth(month, "month"))));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 기간 골라 기록 지우기 (M-07) ]",
            description =
                    """
                    **Parameters** from·to(한국 날짜 yyyy-MM-dd, 필수, 둘 다 포함, 최대 366일)
                    **Returns** from, to, deletedReadings(지운 혈당 수), deletedMeals(지운 식사 수), deletedInsulinLogs(지운 인슐린 수). 이미 지운 기록은 안 셈.
                    혈당은 잰 시각, 식사는 시작 시각, 인슐린은 맞은 시각으로 기간에 드는지 보고 지운다(하나 지우기와 같은 소프트 삭제).
                    기간 안 식사에 붙은 혈당은 기간 밖이어도 같이 지우고 deletedReadings에 센다(예: to 날 23:30 저녁 + 다음 날 01:30 식후 혈당).
                    기간 안 혈당이 기간 밖 식사에 붙어 있으면 혈당만 지운다(하나 지우기와 같음).
                    지운 식사의 아직 안 보낸 식후 알림은 취소하고, 지운 식사의 알림은 통계·리포트 「놓친 측정」에서 빠진다. 지운 혈당이 끝낸 알림은 풀린다.
                    되돌리기는 없음 — 응답에 지운 id가 없으니 화면은 되돌리기를 띄우지 않는다.
                    날짜 형식이 틀리거나 from > to 거나 366일 넘으면 GLOBAL_40001
                    """)
    @DeleteMapping
    public ResponseEntity<BaseResponse<RecordDeleteResponse>> deleteInRange(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(BaseResponse.success(200, "기간 기록을 지웠습니다.",
                recordDeleteService.deleteInRange(principal.userId(),
                        QueryParams.requiredDate(from, "from"),
                        QueryParams.requiredDate(to, "to"))));
    }
}
