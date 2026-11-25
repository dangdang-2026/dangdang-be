package com.dangdang.domain.pregnancy.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

// 관리 모드 (M-03). 앱은 바꾸라고 권하지 않는다 — 사용자가 병원에서 들은 대로 고른다
public enum CareMode {
    DIET,      // 식사·운동 (화면 이름 「식사 관리」)
    INSULIN,   // 인슐린 함께 → 홈에 인슐린 기록 버튼 (INS-01)
    ORAL;      // 먹는 약 함께 → 리포트 머리말에만 표시

    // JSON → enum은 이름 글자 그대로만 받는다
    // 이게 없으면 Jackson이 숫자 1을 「두 번째 값」(INSULIN)으로 읽어 버린다
    @JsonCreator
    public static CareMode from(String value) {
        for (CareMode mode : values()) {
            if (mode.name().equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("관리 모드는 DIET, INSULIN, ORAL 중 하나예요.");
    }
}
