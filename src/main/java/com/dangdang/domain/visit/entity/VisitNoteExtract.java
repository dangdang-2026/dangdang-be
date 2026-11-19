package com.dangdang.domain.visit.entity;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.visit.enums.ExtractStatus;
import com.dangdang.global.common.QueryParams;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

// 진료 메모 AI 정리 결과 (visit_notes.ai_extract JSONB에 그대로 저장, 응답에도 그대로 나감)
// 메모에 없는 칸은 null (AI가 지어내지 않음). 처방·약 용량은 뽑지 않음
// 모르는 칸이 와도 읽다가 실패하지 않게 ignoreUnknown (AI 서버가 칸을 늘려도 저장된 결과를 계속 읽을 수 있게)
@JsonIgnoreProperties(ignoreUnknown = true)
public record VisitNoteExtract(
        Field<List<MeasureSlot>> measureSlots,   // 잴 때 (하루 순서)
        Field<Integer> fastingTarget,            // 공복 목표 (미만)
        Field<Integer> postMealTarget,           // 식후 목표 (미만)
        Field<Integer> postMealMinutes,          // 식후 몇 분에 잴지 (30~180)
        Field<LocalDate> nextVisitDate,          // 다음 진료일
        boolean insulinSkipped,                  // 인슐린·약 문장이 있어서 AI에 안 보냄 → 「약은 직접 적어 주세요」
        String promptVersion) {                  // 어떤 프롬프트 판으로 정리했는지

    // 기준 등록 API(CriteriaVersionCreateRequest)와 같은 범위. 밖이면 그대로 적용할 수 없으니 「확인 필요」로
    private static final int MIN_TARGET = 1;
    private static final int MAX_TARGET = 600;
    private static final int MIN_POST_MEAL_MINUTES = 30;
    private static final int MAX_POST_MEAL_MINUTES = 180;

    // 한 칸: 뽑은 값 + 근거 문장(메모에 있는 그대로) + 확신 정도
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Field<T>(T value, String evidence, ExtractStatus status) {
    }

    // AI 응답을 그대로 믿지 않고 화면에 내보낼 모양으로 정리
    // - 칸은 있는데 값이 비었으면(null·빈 목록) 메모에 없는 것과 같으니 칸을 null로
    // - 확신 정도(status)가 없거나 값이 기준 범위 밖이면 「확인 필요」(체크 해제)로 바꿈 → 사용자가 보고 고르게
    @JsonIgnore
    public VisitNoteExtract normalized() {
        return new VisitNoteExtract(
                // contains(null)은 List.of 목록에서 NPE가 나서 stream으로 봄
                check(measureSlots, v -> v.stream().noneMatch(Objects::isNull)),
                check(fastingTarget, v -> v >= MIN_TARGET && v <= MAX_TARGET),
                check(postMealTarget, v -> v >= MIN_TARGET && v <= MAX_TARGET),
                check(postMealMinutes, v -> v >= MIN_POST_MEAL_MINUTES && v <= MAX_POST_MEAL_MINUTES && v % 10 == 0),
                check(nextVisitDate, v -> v.getYear() >= QueryParams.MIN_YEAR && v.getYear() <= QueryParams.MAX_YEAR),
                insulinSkipped,
                promptVersion);
    }

    private static <T> Field<T> check(Field<T> field, Predicate<T> inRange) {
        if (field == null || field.value() == null
                || (field.value() instanceof List<?> list && list.isEmpty())) {
            return null;
        }
        if (field.status() == null || !inRange.test(field.value())) {
            return new Field<>(field.value(), field.evidence(), ExtractStatus.CHECK_NEEDED);
        }
        return field;
    }
}
