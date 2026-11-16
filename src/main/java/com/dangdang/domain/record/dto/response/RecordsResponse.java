package com.dangdang.domain.record.dto.response;

import java.time.LocalDate;
import java.util.List;

// 기록 통합 조회 응답. from~to 모든 날짜가 days에 들어감 (기록 없는 날은 events가 [])
public record RecordsResponse(
        LocalDate from,
        LocalDate to,
        List<Day> days) {

    public record Day(
            LocalDate date,
            List<RecordEventResponse> events) {   // 시각 오래된 순
    }
}
