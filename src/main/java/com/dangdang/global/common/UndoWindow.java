package com.dangdang.global.common;

import java.time.Duration;
import java.time.Instant;

// 지운 기록을 「되돌리기」할 수 있는 시간 (D-08 토스트). 혈당·식사가 같이 쓴다
public final class UndoWindow {

    public static final Duration DURATION = Duration.ofMinutes(5);

    private UndoWindow() {
    }

    // 이 시각 이후에 지운 것만 되돌릴 수 있다 (딱 5분 전에 지운 것까지 됨)
    public static Instant since(Instant now) {
        return now.minus(DURATION);
    }
}
