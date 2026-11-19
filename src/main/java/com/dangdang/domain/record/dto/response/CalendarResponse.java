package com.dangdang.domain.record.dto.response;

import com.dangdang.domain.record.enums.CalendarDayStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

// 기록 달력 한 달 응답. 날짜를 누르면 화면이 GET /api/records 로 그날 기록을 부름
// firstRecordDate는 null이어도 키를 남겨 둠 (기록이 아직 없다는 뜻)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CalendarResponse(
        YearMonth month,             // "2026-10" 형태로 나감
        LocalDate firstRecordDate,   // 처음 기록한 날 (한국 날짜). 기록이 하나도 없으면 null
        boolean hasPrevMonth,        // 가입한 달보다 뒤인 달이면 true
        boolean hasNextMonth,        // 이번 달보다 앞인 달이면 true
        List<Day> days) {            // 그 달 1일 ~ 말일

    // Swagger 이름을 따로 줌 (RecordsResponse.Day와 이름이 같아서 문서에서 하나로 합쳐지던 문제)
    @Schema(name = "CalendarDay")
    public record Day(
            LocalDate date,
            CalendarDayStatus status,
            boolean visit) {         // 그날 진료 일정이 있는지 (날짜 미정·지운 일정은 안 셈)
    }
}
