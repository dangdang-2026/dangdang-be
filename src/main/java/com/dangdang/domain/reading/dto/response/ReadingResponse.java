package com.dangdang.domain.reading.dto.response;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.reading.entity.Reading;
import com.dangdang.domain.reading.enums.MeterLimit;
import com.dangdang.domain.reading.enums.ReadingTiming;
import com.dangdang.domain.reading.enums.ReadingType;
import com.dangdang.domain.reading.service.ReadingEvaluator;

import java.time.Instant;

// 혈당 기록 하나 응답. 판정 칸(targetValue~timing)은 저장된 값이 아니라 응답 만들 때 계산함
// 색·문구(「목표보다 높아요」)는 화면 몫이라 여기선 사실만 줌
public record ReadingResponse(
        Long readingId,
        Integer value,
        MeterLimit meterLimit,    // HI·LO로 적었으면 그 글자 (value는 600·20). 숫자면 null (G-11)
        Instant measuredAt,
        ReadingType readingType,
        MeasureSlot slot,
        Long criteriaVersionId,
        Long mealId,
        boolean enteredLate,
        Integer targetValue,      // 잴 때 적용 중이던 목표 (G-03 「140 미만」). 기타·목표 없음이면 null
        Boolean aboveTarget,      // 목표 이상이면 true. 목표가 없으면 null
        boolean belowLow,         // 70 미만 (D-04 · G-05)
        Instant expectedAt,       // 재야 할 시각 (식후만, 모르면 null) — D-01 「n분 늦게 측정」
        ReadingTiming timing,     // 재야 할 시각과 비교 EARLY·ON_TIME·LATE (모르면 null)
        Integer originalValue,    // 처음 저장한 값. 값을 한 번도 안 고쳤으면 null (D-05 「처음 128」)
        Instant editedAt,         // 사용자가 마지막으로 고친 시각. 안 고쳤으면 null (D-07 「수정됨」)
        Instant createdAt,
        Instant updatedAt) {

    // 재야 할 시각을 안 구한 곳(혈당 목록 GET /api/readings 페이지)용. expectedAt·timing이 null
    public static ReadingResponse from(Reading r) {
        return of(r, null);
    }

    // expectedAt = ExpectedTimeResolver로 구한 재야 할 시각 (모르면 null)
    public static ReadingResponse of(Reading r, Instant expectedAt) {
        return new ReadingResponse(
                r.getId(), r.getValue(), r.getMeterLimit(), r.getMeasuredAt(), r.getReadingType(), r.getSlot(),
                r.getCriteriaVersion().getId(),   // id만 꺼내는 건 DB를 또 조회하지 않는다
                r.getMealId(), r.isEnteredLate(),
                ReadingEvaluator.targetOf(r), ReadingEvaluator.aboveTarget(r), ReadingEvaluator.belowLow(r),
                expectedAt, ReadingEvaluator.timing(r.getMeasuredAt(), expectedAt),
                r.getOriginalValue(), r.getEditedAt(),
                r.getCreatedAt(), r.getUpdatedAt());
    }
}
