package com.dangdang.domain.insulin.enums;

// 인슐린 종류 (E-02 종류 칩)
public enum InsulinType {
    MEAL,      // 식사 때 (속효성)
    BEDTIME,   // 자기 전 (지속형)
    CUSTOM     // 직접 입력 (이름은 따로 저장하지 않음)
}
