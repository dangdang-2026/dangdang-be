package com.dangdang.domain.visit.entity;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.visit.enums.ExtractStatus;
import com.dangdang.global.common.QueryParams;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

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
        boolean insulinSkipped,                  // 인슐린·약 문장이 있어서 AI 서버가 걸러 냄 → 「약은 직접 적어 주세요」
        String promptVersion) {                  // 어떤 프롬프트 판으로 정리했는지

    // 기준 등록 API(CriteriaVersionCreateRequest)와 같은 범위. 밖이면 그대로 적용할 수 없으니 「확인 필요」로
    private static final int MIN_TARGET = 1;
    private static final int MAX_TARGET = 600;
    private static final int MIN_POST_MEAL_MINUTES = 30;
    private static final int MAX_POST_MEAL_MINUTES = 180;

    // 근거 문장을 메모와 비교할 때: AI가 앞뒤를 줄여 「…」로 붙이기도 해서 그 앞뒤 조각을 따로 봄
    private static final Pattern ELLIPSIS = Pattern.compile("…|\\.\\.\\.");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    // 한 칸: 뽑은 값 + 근거 문장(메모에 있는 그대로) + 확신 정도
    // 처음 보는 값(예: 새 상태·새 잴 때)이 와도 응답 전체가 실패하지 않게 null로 읽음 → 정리할 때 「확인 필요」로 바뀜
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Field<T>(
            @JsonFormat(with = JsonFormat.Feature.READ_UNKNOWN_ENUM_VALUES_AS_NULL) T value,
            String evidence,
            @JsonFormat(with = JsonFormat.Feature.READ_UNKNOWN_ENUM_VALUES_AS_NULL) ExtractStatus status) {
    }

    // AI 응답을 그대로 믿지 않고 화면에 내보낼 모양으로 정리
    // - 칸은 있는데 값이 비었으면(null·빈 목록) 메모에 없는 것과 같으니 칸을 null로
    // - 아래 중 하나라도 걸리면 「확인 필요」(체크 해제)로 바꿈 → 사용자가 보고 고르게
    //   확신 정도(status)가 없음 / 값이 기준 범위 밖 / 근거 문장이 메모에 없음(AI가 지어냈을 수 있음)
    //   다음 진료일이 이번 진료일보다 앞이거나 같음 / 잴 때에 모르는 값이 섞임
    // - 잴 때는 겹치는 걸 빼고 하루 순서로 정렬
    @JsonIgnore
    public VisitNoteExtract normalized(String memo, LocalDate visitDate) {
        String flatMemo = flatten(memo);
        return new VisitNoteExtract(
                normalizeSlots(measureSlots, flatMemo),
                check(fastingTarget, flatMemo, v -> v >= MIN_TARGET && v <= MAX_TARGET),
                check(postMealTarget, flatMemo, v -> v >= MIN_TARGET && v <= MAX_TARGET),
                check(postMealMinutes, flatMemo, v -> v >= MIN_POST_MEAL_MINUTES && v <= MAX_POST_MEAL_MINUTES && v % 10 == 0),
                check(nextVisitDate, flatMemo, v -> v.isAfter(visitDate)
                        && v.getYear() >= QueryParams.MIN_YEAR && v.getYear() <= QueryParams.MAX_YEAR),
                insulinSkipped,
                promptVersion);
    }

    // 잴 때: 모르는 값(null)은 빼고 「확인 필요」, 겹치면 하나만, 하루 순서로
    private static Field<List<MeasureSlot>> normalizeSlots(Field<List<MeasureSlot>> field, String flatMemo) {
        if (field == null || field.value() == null) {
            return null;
        }
        List<MeasureSlot> slots = field.value().stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        if (slots.isEmpty()) {
            return null;
        }
        boolean hadUnknown = field.value().stream().anyMatch(Objects::isNull);
        Field<List<MeasureSlot>> sorted = new Field<>(slots, field.evidence(), field.status());
        return hadUnknown ? checkNeeded(sorted) : check(sorted, flatMemo, v -> true);
    }

    private static <T> Field<T> check(Field<T> field, String flatMemo, Predicate<T> inRange) {
        if (field == null || field.value() == null
                || (field.value() instanceof List<?> list && list.isEmpty())) {
            return null;
        }
        if (field.status() == null || !inRange.test(field.value()) || !inMemo(field.evidence(), flatMemo)) {
            return checkNeeded(field);
        }
        return field;
    }

    private static <T> Field<T> checkNeeded(Field<T> field) {
        return new Field<>(field.value(), field.evidence(), ExtractStatus.CHECK_NEEDED);
    }

    // 근거 문장이 메모에 그대로 있는지 (공백·줄바꿈 차이는 무시, 「…」로 줄인 경우 조각마다 확인)
    private static boolean inMemo(String evidence, String flatMemo) {
        if (evidence == null) {
            return false;
        }
        List<String> pieces = Arrays.stream(ELLIPSIS.split(evidence))
                .map(VisitNoteExtract::flatten)
                .filter(p -> !p.isEmpty())
                .toList();
        return !pieces.isEmpty() && pieces.stream().allMatch(flatMemo::contains);
    }

    private static String flatten(String s) {
        return SPACES.matcher(s).replaceAll(" ").strip();
    }
}
