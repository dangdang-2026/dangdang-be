package com.dangdang.domain.reading.dto.response;

import com.dangdang.domain.meal.enums.MealSlot;
import com.dangdang.domain.reading.enums.ReadingType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

// 혈당 통계 응답 (D-17~D-21). 공복과 식후는 섞지 않고, 해석·판정 문구는 안 줌 (숫자만)
// null인 칸도 키를 남겨 둠 — previous가 null이면 "비교할 지난 기록 없음"(D-20)이라 화면이 구분해야 함
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReadingStatsResponse(
        ReadingType slot,                 // FASTING 또는 POST_MEAL
        Range range,                      // 보는 기간
        Range compareRange,               // 바로 앞 같은 길이 기간
        Integer targetValue,              // 지금 병원 목표. 없으면 null
        Summary current,
        Summary previous,                 // 비교 기간에 기록이 없으면 null
        List<Point> series,               // 보는 기간 날짜별 (기록 없는 날은 average null, count 0)
        List<Point> previousSeries,       // 비교 기간 날짜별. previous가 null이면 null
        List<MealAverage> byMeal,         // 끼니별 평균. 공복이면 []
        LocalDate firstRecordDate) {      // 처음 잰 날 (한국, 공복·식후·기타 전부). 기록이 없으면 null — D-20 「9월 21일부터」

    @Schema(name = "StatsRange")
    public record Range(LocalDate from, LocalDate to) {
    }

    @Schema(name = "StatsSummary")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Summary(
            Integer average,          // 평균 (반올림). 기록이 없으면 null
            Double inTargetRate,      // 목표 안 비율 0~1 (소수 둘째 자리) = inTarget ÷ judged. judged가 0이면 null
            int count,                // 측정 횟수 (목표가 없던 기록 포함)
            long missed,              // 알림이 갔는데 혈당을 안 남긴 횟수
            int overDays,             // 목표를 넘은 기록이 하나라도 있는 날 수
            int judged,               // 그때 목표와 비교한 기록 수 (D-17 「86번 중」)
            int inTarget,             // 그중 목표 안 (D-17 「71번」)
            int judgedDays,           // 목표와 비교한 기록이 있는 날 수 (D-21 「/ 6일」)
            int inTargetDays,         // 그중 목표를 한 번도 안 넘은 날 수 = judgedDays − overDays (D-21 「5일」)
            int belowLow) {           // 70 미만 횟수 (D-17 「70 미만 1번」)
    }

    @Schema(name = "StatsPoint")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Point(LocalDate date, Integer average, int count) {
    }

    @Schema(name = "StatsMealAverage")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MealAverage(MealSlot meal, Integer average, Integer previousAverage) {
    }
}
