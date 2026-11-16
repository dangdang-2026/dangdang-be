package com.dangdang.domain.record.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

// 기록 통합 조회 응답. from~to 모든 날짜가 days에 들어감 (기록 없는 날은 events가 [])
public record RecordsResponse(
        LocalDate from,
        LocalDate to,
        List<Day> days) {

    // Swagger 이름을 따로 줌 (CalendarResponse.Day와 이름이 같아서 문서에서 하나로 합쳐지던 문제)
    @Schema(name = "RecordDay")
    public record Day(
            LocalDate date,
            List<RecordEventResponse> events) {   // 시각 오래된 순
    }
}
