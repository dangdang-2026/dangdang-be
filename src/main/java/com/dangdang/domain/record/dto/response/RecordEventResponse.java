package com.dangdang.domain.record.dto.response;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.insulin.entity.InsulinLog;
import com.dangdang.domain.insulin.enums.InsulinType;
import com.dangdang.domain.meal.dto.response.MealItemSummaryResponse;
import com.dangdang.domain.meal.entity.Meal;
import com.dangdang.domain.meal.enums.MealSlot;
import com.dangdang.domain.reading.entity.Reading;
import com.dangdang.domain.reading.enums.MeterLimit;
import com.dangdang.domain.reading.enums.ReadingTiming;
import com.dangdang.domain.reading.enums.ReadingType;
import com.dangdang.domain.reading.service.ReadingEvaluator;
import com.dangdang.domain.record.enums.RecordEventType;
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
        boolean modified,        // 사용자가 저장한 뒤 고친 적이 있는지 (혈당은 editedAt, 식사는 meals.edited_at, 인슐린은 editedAt)
        ReadingPayload reading,  // type=READING일 때만
        MealPayload meal,        // type=MEAL일 때만
        InsulinPayload insulin) {   // type=INSULIN일 때만

    // expectedAt = 재야 할 시각 (식후만, 모르면 null)
    public static RecordEventResponse from(Reading r, Instant expectedAt) {
        return new RecordEventResponse(RecordEventType.READING, r.getId(), r.getMeasuredAt(),
                // 사용자가 고친 적이 있는지 (지우기·되돌리기·식사만 붙이기는 안 셈)
                // edited_at 칸이 생기기 전에 고친 혈당은 비워 둬서 false (V20261124_90 — 식사만 붙인 것과 가릴 수 없어서 일부러)
                r.getEditedAt() != null,
                new ReadingPayload(r.getValue(), r.getMeterLimit(), r.getReadingType(), r.getSlot(), r.getMealId(),
                        r.getCriteriaVersion().getId(), r.getScheduledAt(), r.isEnteredLate(),
                        ReadingEvaluator.targetOf(r), ReadingEvaluator.aboveTarget(r), ReadingEvaluator.belowLow(r),
                        expectedAt, ReadingEvaluator.timing(r.getMeasuredAt(), expectedAt),
                        r.getOriginalValue(), r.getOriginalMeterLimit(), r.getEditedAt()),
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

    // 아래 칸들은 null이어도 키를 남겨 둠 (바깥 NON_NULL은 이 안까지 적용 안 됨)
    public record ReadingPayload(
            Integer value,
            MeterLimit meterLimit,    // HI·LO로 적었으면 그 글자 (value는 600·20). 숫자면 null
            ReadingType readingType,
            MeasureSlot slot,
            Long mealId,
            Long criteriaVersionId,
            Instant scheduledAt,      // 늘 null (넣는 코드 없음) — 재야 할 시각은 expectedAt을 씀
            boolean enteredLate,
            Integer targetValue,      // 잴 때 목표. 없으면 null
            Boolean aboveTarget,      // 목표 이상이면 true. 목표가 없으면 null (D-01 「목표보다 높아요」)
            boolean belowLow,         // 70 미만 (D-04 「목표보다 낮아요」)
            Instant expectedAt,       // 재야 할 시각 (D-01 「n분 늦게 측정」)
            ReadingTiming timing,     // EARLY·ON_TIME·LATE (모르면 null)
            Integer originalValue,    // 처음 값 (안 고쳤거나 처음 값으로 되돌렸으면 null)
            MeterLimit originalMeterLimit,   // 처음 저장 때 HI·LO였으면 그 글자. 아니면 null
            Instant editedAt) {       // 고친 시각 (안 고쳤으면 null)
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
