package com.dangdang.domain.criteria.dto.request;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CriteriaVersionCreateRequest(
        @Min(value = 30, message = "식후 측정 시간은 30분~3시간, 10분 단위로 골라 주세요.")
        @Max(value = 180, message = "식후 측정 시간은 30분~3시간, 10분 단위로 골라 주세요.")
        Integer postMealMinutes,              // null = 아직 몰라요 (null이면 @Min·@Max는 검사 안 하고 통과)

        List<MeasureSlot> measureSlots,       // null·빈 배열 = 아직 안 정함

        @Min(value = 1, message = "목표 숫자를 다시 확인해 주세요.")
        @Max(value = 600, message = "목표 숫자를 다시 확인해 주세요.")
        Integer fastingTarget,

        @Min(value = 1, message = "목표 숫자를 다시 확인해 주세요.")
        @Max(value = 600, message = "목표 숫자를 다시 확인해 주세요.")
        Integer postMealTarget,

        @Size(max = 255, message = "메모는 255자까지 적을 수 있어요.")
        String sourceNote,

        LocalDate effectiveFrom) {            // null = 오늘. JSON은 "2026-10-29"

    // 10분 단위 검사 — 이름이 is…로 시작하는 메서드에 @AssertTrue를 붙이면, @Valid가 이걸 불러서 false면 막는다
    @AssertTrue(message = "식후 측정 시간은 30분~3시간, 10분 단위로 골라 주세요.")
    public boolean isPostMealMinutesTenStep() {
        return postMealMinutes == null || postMealMinutes % 10 == 0;   // null이면 통과, 아니면 10의 배수만
    }
}
