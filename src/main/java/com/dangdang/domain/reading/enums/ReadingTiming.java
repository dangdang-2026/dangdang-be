package com.dangdang.domain.reading.enums;

// 재야 할 시각과 비교해서 언제 쟀는지 (예정 시각 ±15분 안이면 제때)
public enum ReadingTiming {
    EARLY,      // 예정보다 15분 넘게 일찍
    ON_TIME,    // 제때
    LATE        // 예정보다 15분 넘게 늦게
}
