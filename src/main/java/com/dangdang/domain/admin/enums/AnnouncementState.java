package com.dangdang.domain.admin.enums;

import java.time.Instant;

// 운영자 화면에서 보는 당당 소식 상태. DB에는 상태 칸이 없고 시작·끝 시각으로 정한다
public enum AnnouncementState {
    LIVE,       // 올라가 있음
    SCHEDULED,  // 예약 (시작 전)
    ENDED,      // 끝남 (기간이 끝났거나 운영자가 내림)
    CANCELED;   // 예약 취소 — 시작 전에 내리면 Announcement.end가 끝 = 시작으로 둔다

    public static AnnouncementState of(Instant from, Instant to, Instant now) {
        if (to != null && !to.isAfter(from)) {
            return CANCELED;
        }
        if (to != null && !to.isAfter(now)) {
            return ENDED;
        }
        return from.isAfter(now) ? SCHEDULED : LIVE;
    }

    // 「내린 소식」 탭에는 끝남과 예약 취소를 같이 보여 준다
    public boolean isOver() {
        return this == ENDED || this == CANCELED;
    }
}
