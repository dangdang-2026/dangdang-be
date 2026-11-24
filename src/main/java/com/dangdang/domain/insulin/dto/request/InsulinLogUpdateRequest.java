package com.dangdang.domain.insulin.dto.request;

import com.dangdang.domain.insulin.enums.InsulinType;
import com.dangdang.domain.meal.enums.MealSlot;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.Instant;

// 인슐린 기록 수정 요청 (PATCH, D-10) — 보낸 칸만 바뀜, 안 보낸 칸(null)은 그대로 둠 (null로 지우기는 없음)
// 끼니는 종류를 BEDTIME으로 바꾸면 서버가 지움
public record InsulinLogUpdateRequest(
        InsulinType insulinType,

        MealSlot mealSlot,

        @DecimalMin(value = "0", inclusive = false, message = "단위는 0보다 크게 입력해 주세요.")
        @DecimalMax(value = "999.9", message = "단위는 999.9까지 입력할 수 있어요.")
        @Digits(integer = 3, fraction = 1, message = "단위는 소수 한 자리까지 입력할 수 있어요.")
        BigDecimal units,

        @PastOrPresent(message = "맞은 시각은 지금보다 뒤일 수 없어요.")
        Instant takenAt) {

    // 바꿀 값이 하나도 없는 요청인지. Swagger·JSON에 "empty" 칸으로 안 보이게 뺌
    @JsonIgnore
    public boolean isEmpty() {
        return insulinType == null && mealSlot == null && units == null && takenAt == null;
    }
}
