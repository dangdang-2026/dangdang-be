package com.dangdang.domain.reading.dto.response;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.reading.entity.Reading;
import com.dangdang.domain.reading.enums.ReadingType;

import java.time.Instant;

// 혈당 기록 하나 응답
public record ReadingResponse(
        Long readingId,
        Integer value,
        Instant measuredAt,
        ReadingType readingType,
        MeasureSlot slot,
        Long criteriaVersionId,
        Long mealId,
        boolean enteredLate,
        Instant createdAt,
        Instant updatedAt) {

    // 엔티티 -> 응답
    public static ReadingResponse from(Reading r) {
        return new ReadingResponse(
                r.getId(), r.getValue(), r.getMeasuredAt(), r.getReadingType(), r.getSlot(),
                r.getCriteriaVersion().getId(),   // id만 꺼내는 건 DB를 또 조회하지 않는다
                r.getMealId(), r.isEnteredLate(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
