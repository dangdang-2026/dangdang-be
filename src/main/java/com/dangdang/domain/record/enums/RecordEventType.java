package com.dangdang.domain.record.enums;

// 기록 통합 조회에 섞여 나오는 기록 종류. 같은 시각이면 이 순서(MEAL → READING → INSULIN)로 줄 세움
public enum RecordEventType {
    MEAL,
    READING,
    INSULIN
}
