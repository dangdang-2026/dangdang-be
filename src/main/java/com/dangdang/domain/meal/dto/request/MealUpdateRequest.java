package com.dangdang.domain.meal.dto.request;

import com.dangdang.domain.meal.enums.MealSlot;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;

// 식사 기록 수정 요청 (PATCH) — 보낸 칸만 바뀜, 안 보낸 칸(null)은 그대로 둠
// startedOffline은 "그때 오프라인이었는지"라는 사실이라 수정 대상이 아님
public record MealUpdateRequest(
        MealSlot mealSlot,

        @PastOrPresent(message = "식사 시작 시각은 지금보다 뒤일 수 없어요.")
        Instant startedAt,           // 등록과 같은 형식 ("2026-11-09T12:30:00+09:00")

        @Size(max = 255, message = "메모는 255자까지 쓸 수 있어요.")
        String note,                 // 메모를 비우려면 "" 로 보냄

        Instant expectedUpdatedAt) { // 화면을 열 때 받은 식사 updatedAt (선택, 항목 응답의 mealUpdatedAt도 됨). 그 사이 다른 곳에서 바뀌었으면 409 (#172)

    // 바꿀 값이 하나도 없는 요청인지 (expectedUpdatedAt은 바꿀 값이 아님)
    public boolean isEmpty() {
        return mealSlot == null && startedAt == null && note == null;
    }
}
