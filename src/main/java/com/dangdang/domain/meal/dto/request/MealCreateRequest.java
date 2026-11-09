package com.dangdang.domain.meal.dto.request;

import com.dangdang.domain.meal.enums.MealSlot;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;

// 식사 시작 기록 요청
public record MealCreateRequest(
        @NotNull(message = "아침·점심·저녁·간식 중 하나를 골라 주세요.")
        MealSlot mealSlot,

        @NotNull(message = "식사 시작 시각을 입력해 주세요.")
        @PastOrPresent(message = "식사 시작 시각은 지금보다 뒤일 수 없어요.")
        Instant startedAt,           // JSON은 "2026-11-09T12:30:00+09:00" 처럼 시간대 포함

        Boolean startedOffline,      // 안 보내면 false

        @Size(max = 255, message = "메모는 255자까지 쓸 수 있어요.")
        String note) {               // 없어도 됨

    // 안 보낸 경우 false로 봄
    public boolean isStartedOffline() {
        return Boolean.TRUE.equals(startedOffline);
    }
}
