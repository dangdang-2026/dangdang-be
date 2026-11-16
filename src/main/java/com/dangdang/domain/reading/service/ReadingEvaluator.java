package com.dangdang.domain.reading.service;

import com.dangdang.domain.criteria.entity.CriteriaVersion;
import com.dangdang.domain.reading.entity.Reading;
import com.dangdang.domain.reading.enums.ReadingTiming;

import java.time.Duration;
import java.time.Instant;

// 혈당 기록 하나를 보고 "제때 쟀는지 · 목표를 넘었는지" 계산함 (DB에 저장하지 않고 응답 만들 때마다 계산)
// 색·평가 문구는 화면 몫이라 여기선 사실만 돌려줌
public final class ReadingEvaluator {

    // 예정 시각에서 이만큼 안쪽이면 제때
    private static final Duration ON_TIME_RANGE = Duration.ofMinutes(15);

    private ReadingEvaluator() {
    }

    // 재야 할 시각. 알림 예정 시각이 있으면 그것, 없으면 식사 시작 + 병원 기준 식후 시간(분)
    // 둘 다 모르면 null (공복·자기 전 혈당이나, 식사를 안 붙인 식후 혈당)
    public static Instant expectedAt(Reading reading, Instant mealStartedAt) {
        if (reading.getScheduledAt() != null) {
            return reading.getScheduledAt();
        }
        Integer postMealMinutes = reading.getCriteriaVersion().getPostMealMinutes();
        if (mealStartedAt == null || postMealMinutes == null) {
            return null;
        }
        return mealStartedAt.plus(Duration.ofMinutes(postMealMinutes));
    }

    // 잰 시각을 재야 할 시각과 비교함. 재야 할 시각을 모르면 null
    public static ReadingTiming timing(Instant measuredAt, Instant expectedAt) {
        if (expectedAt == null) {
            return null;
        }
        Duration diff = Duration.between(expectedAt, measuredAt);   // +면 늦게, -면 일찍
        if (diff.compareTo(ON_TIME_RANGE) > 0) {
            return ReadingTiming.LATE;
        }
        if (diff.compareTo(ON_TIME_RANGE.negated()) < 0) {
            return ReadingTiming.EARLY;
        }
        return ReadingTiming.ON_TIME;
    }

    // 잴 때 적용 중이던 병원 목표를 넘었는지. 목표는 "미만"이라 목표값과 같아도 넘은 것으로 봄
    // 목표가 없거나 공복·식후가 아닌 혈당이면 null (비교할 기준이 없음)
    public static Boolean aboveTarget(Reading reading) {
        CriteriaVersion criteria = reading.getCriteriaVersion();
        Integer target = switch (reading.getReadingType()) {
            case FASTING -> criteria.getFastingTarget();
            case POST_MEAL -> criteria.getPostMealTarget();
            case OTHER -> null;
        };
        return target == null ? null : reading.getValue() >= target;
    }
}
