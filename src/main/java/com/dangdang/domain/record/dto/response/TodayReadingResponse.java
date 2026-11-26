package com.dangdang.domain.record.dto.response;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.reading.entity.Reading;
import com.dangdang.domain.reading.enums.MeterLimit;
import com.dangdang.domain.reading.enums.ReadingTiming;
import com.dangdang.domain.reading.enums.ReadingType;
import com.dangdang.domain.reading.service.ReadingEvaluator;

import java.time.Instant;

// 오늘 화면의 혈당 하나. timing·aboveTarget·targetValue·belowLow는 저장된 값이 아니라 응답 만들 때 계산함
public record TodayReadingResponse(
        Long readingId,
        Integer value,
        MeterLimit meterLimit,    // HI·LO로 적었으면 그 글자. 숫자면 null
        ReadingType readingType,
        MeasureSlot slot,
        Long mealId,
        Instant measuredAt,
        ReadingTiming timing,     // 재야 할 시각을 모르면 null
        Boolean aboveTarget,      // 목표가 없으면 null
        Integer targetValue,      // 잴 때 적용 중이던 목표. 없으면 null
        boolean belowLow,         // 70 미만 (H-06b 낮은 값)
        Instant expectedAt,       // 재야 할 시각 (모르면 null)
        Instant updatedAt) {      // 이 기록의 마지막 수정 시각. 고치기의 expectedUpdatedAt으로 그대로 보냄 (#172)

    // expectedAt = ExpectedTimeResolver로 구한 재야 할 시각 (모르면 null)
    public static TodayReadingResponse of(Reading r, Instant expectedAt) {
        return new TodayReadingResponse(
                r.getId(), r.getValue(), r.getMeterLimit(), r.getReadingType(), r.getSlot(), r.getMealId(),
                r.getMeasuredAt(),
                ReadingEvaluator.timing(r.getMeasuredAt(), expectedAt),
                ReadingEvaluator.aboveTarget(r),
                ReadingEvaluator.targetOf(r),
                ReadingEvaluator.belowLow(r),
                expectedAt,
                r.getUpdatedAt());
    }
}
