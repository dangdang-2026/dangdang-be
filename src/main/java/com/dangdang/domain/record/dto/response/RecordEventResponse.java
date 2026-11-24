package com.dangdang.domain.record.dto.response;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.insulin.entity.InsulinLog;
import com.dangdang.domain.insulin.enums.InsulinType;
import com.dangdang.domain.meal.dto.response.MealItemSummaryResponse;
import com.dangdang.domain.meal.entity.Meal;
import com.dangdang.domain.meal.enums.MealSlot;
import com.dangdang.domain.reading.entity.Reading;
import com.dangdang.domain.reading.enums.ReadingType;
import com.dangdang.domain.record.enums.RecordEventType;
import com.dangdang.global.common.BaseTimeEntity;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// 기록 통합 조회의 기록 하나. type에 맞는 칸(reading·meal·insulin) 하나만 들어가고 나머지는 응답에서 빠짐
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecordEventResponse(
        RecordEventType type,
        Long id,
        Instant occurredAt,      // 혈당은 잰 시각, 식사는 시작 시각, 인슐린은 맞은 시각
        boolean modified,        // 저장한 뒤 고친 적이 있는지
        ReadingPayload reading,  // type=READING일 때만
        MealPayload meal,        // type=MEAL일 때만
        InsulinPayload insulin) {   // type=INSULIN일 때만

    public static RecordEventResponse from(Reading r) {
        return new RecordEventResponse(RecordEventType.READING, r.getId(), r.getMeasuredAt(), isModified(r),
                new ReadingPayload(r.getValue(), r.getReadingType(), r.getSlot(), r.getMealId(),
                        r.getCriteriaVersion().getId(), r.getScheduledAt(), r.isEnteredLate()),
                null, null);
    }

    // items = 이 식사의 음식 항목 (없으면 [])
    public static RecordEventResponse from(Meal m, List<MealItemSummaryResponse> items) {
        return new RecordEventResponse(RecordEventType.MEAL, m.getId(), m.getStartedAt(), m.isEdited(),   // 음식 항목만 바뀐 건 수정으로 안 셈
                null,
                new MealPayload(m.getMealSlot(), m.isStartedOffline(), m.getNote(), items),
                null);
    }

    // 인슐린은 값이 실제로 바뀐 적이 있으면(editedAt) 수정으로 셈
    public static RecordEventResponse from(InsulinLog l) {
        return new RecordEventResponse(RecordEventType.INSULIN, l.getId(), l.getTakenAt(), l.isEdited(),
                null, null,
                new InsulinPayload(l.getInsulinType(), l.getMealSlot(), l.getUnits()));
    }

    // 처음 저장할 땐 createdAt = updatedAt 이고, 고치면 updatedAt만 늦어짐
    private static boolean isModified(BaseTimeEntity e) {
        return e.getUpdatedAt().isAfter(e.getCreatedAt());
    }

    // 아래 칸들은 null이어도 키를 남겨 둠 (바깥 NON_NULL은 이 안까지 적용 안 됨)
    public record ReadingPayload(
            Integer value,
            ReadingType readingType,
            MeasureSlot slot,
            Long mealId,
            Long criteriaVersionId,
            Instant scheduledAt,
            boolean enteredLate) {
    }

    public record MealPayload(
            MealSlot mealSlot,
            boolean startedOffline,
            String note,
            List<MealItemSummaryResponse> items) {   // 먹은 음식 목록 (추가한 순서, 지운 항목 빼고, 없으면 [])
    }

    // 「인슐린 · 저녁 식사 때 6단위」
    public record InsulinPayload(
            InsulinType insulinType,
            MealSlot mealSlot,       // 식사 때면 아침·점심·저녁, 자기 전이면 null
            BigDecimal units) {      // 6.0 처럼 소수 한 자리
    }
}
