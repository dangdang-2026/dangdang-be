package com.dangdang.domain.insulin.dto.request;

import com.dangdang.domain.insulin.enums.InsulinType;
import com.dangdang.domain.meal.enums.MealSlot;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.Instant;

// 인슐린 기록 등록 요청 (E-02). 끼니는 식사 때(MEAL)일 때만 필수, 나머지 셋은 항상 필수
public record InsulinLogCreateRequest(
        @NotNull(message = "식사 때·자기 전 중 하나를 골라 주세요.")
        InsulinType insulinType,

        MealSlot mealSlot,           // insulinType=MEAL일 때만 (필수, BREAKFAST·LUNCH·DINNER). BEDTIME이면 안 보냄

        @NotNull(message = "맞은 단위를 입력해 주세요.")
        @DecimalMin(value = "0", inclusive = false, message = "단위는 0보다 크게 입력해 주세요.")
        @DecimalMax(value = "999.9", message = "단위는 999.9까지 입력할 수 있어요.")
        @Digits(integer = 3, fraction = 1, message = "단위는 소수 한 자리까지 입력할 수 있어요.")
        BigDecimal units,            // 처방받은 단위를 사용자가 직접 입력 (권장량 아님)

        @NotNull(message = "맞은 시각을 입력해 주세요.")
        @PastOrPresent(message = "맞은 시각은 지금보다 뒤일 수 없어요.")
        Instant takenAt) {           // JSON은 "2026-10-20T09:00:00+09:00"
}
