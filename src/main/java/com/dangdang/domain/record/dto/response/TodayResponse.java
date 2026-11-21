package com.dangdang.domain.record.dto.response;

import com.dangdang.domain.criteria.dto.response.CriteriaVersionResponse;
import com.dangdang.domain.insulin.dto.response.InsulinLogResponse;
import com.dangdang.domain.meal.dto.response.MealResponse;
import com.dangdang.domain.reminder.dto.response.NextReminderResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;

// 오늘 화면(홈) 응답. 어떤 상태 카드를 보여 줄지는 화면이 정하고, 서버는 재료만 모아 줌
// criteria·nextReminder는 없으면 null이어도 키는 남겨 둠 (화면이 "없음"을 구분해야 해서)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TodayResponse(
        LocalDate date,                      // 한국 오늘 날짜
        CriteriaVersionResponse criteria,    // 지금 병원 기준. 한 번도 안 적었으면 null
        List<MealResponse> meals,            // 오늘 식사, 시작 시각 오래된 순 (없으면 [])
        List<TodayReadingResponse> readings, // 오늘 혈당, 잰 시각 오래된 순 (없으면 [])
        List<InsulinLogResponse> insulinLogs, // 오늘 인슐린 기록, 맞은 시각 오래된 순 (없으면 []). 히어로 카드 「아침 인슐린 기록함」
        NextReminderResponse nextReminder,   // 다음 측정 알림. 예약된 게 없으면 null
        boolean pushReady) {                 // 살아 있는 푸시 구독이 있는지 (false면 "알림이 꺼져 있어요")
}
