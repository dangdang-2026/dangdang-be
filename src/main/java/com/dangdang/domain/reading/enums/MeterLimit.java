package com.dangdang.domain.reading.enums;

// 혈당계에 숫자 대신 뜬 글자 (G-11). value에는 혈당계가 잴 수 있는 끝값을 같이 저장함
public enum MeterLimit {
    HI(600),   // 600보다 높음 — 혈당계로 잴 수 없을 만큼 높음
    LO(20);    // 20보다 낮음

    private final int value;

    MeterLimit(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
