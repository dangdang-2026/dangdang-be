package com.dangdang.domain.insulin.enums;

// 인슐린 종류 (E-02 칩 「식사 때 (속효성)」·「자기 전 (지속형)」)
// 최종 디자인에서 「직접 입력」이 빠져서 둘만 둠
public enum InsulinType {
    MEAL,      // 식사 때 (속효성) — 끼니(mealSlot) 필수
    BEDTIME    // 자기 전 (지속형) — 끼니 없음
}
