package com.dangdang.domain.post.enums;

import java.time.Duration;

// 인기 게시판 「기간」 (U-24) — 이 기간 안에 올라온 글 중에서 공감+댓글이 많은 순
public enum PopularPeriod {
    REALTIME(Duration.ofHours(24)),   // 실시간 = 최근 24시간
    WEEK(Duration.ofDays(7)),         // 이번 주 = 최근 7일
    MONTH(Duration.ofDays(30)),       // 이번 달 = 최근 30일
    ALL(null);                        // 기간 없이 — 검색 결과 「인기순」 (U-06)

    private final Duration window;

    PopularPeriod(Duration window) {
        this.window = window;
    }

    // ALL이면 null (기간 조건 없음)
    public Duration window() {
        return window;
    }
}
