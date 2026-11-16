package com.dangdang.domain.record.dto.response;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.reading.entity.Reading;
import com.dangdang.domain.reading.enums.ReadingTiming;
import com.dangdang.domain.reading.enums.ReadingType;
import com.dangdang.domain.reading.service.ReadingEvaluator;

import java.time.Instant;

// 오늘 화면의 혈당 하나. timing·aboveTarget은 저장된 값이 아니라 응답 만들 때 계산함
public record TodayReadingResponse(
        Long readingId,
        Integer value,
        ReadingType readingType,
        MeasureSlot slot,
        Long mealId,
        Instant measuredAt,
        ReadingTiming timing,     // 재야 할 시각을 모르면 null
        Boolean aboveTarget) {    // 목표가 없으면 null

    // expectedAt = 재야 할 시각 (ReadingEvaluator.expectedAt으로 구한 값, 모르면 null)
    public static TodayReadingResponse of(Reading r, Instant expectedAt) {
        return new TodayReadingResponse(
                r.getId(), r.getValue(), r.getReadingType(), r.getSlot(), r.getMealId(), r.getMeasuredAt(),
                ReadingEvaluator.timing(r.getMeasuredAt(), expectedAt),
                ReadingEvaluator.aboveTarget(r));
    }
}
