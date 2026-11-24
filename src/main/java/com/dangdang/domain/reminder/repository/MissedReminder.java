package com.dangdang.domain.reminder.repository;

import com.dangdang.domain.criteria.enums.MeasureSlot;

import java.time.Instant;

// 놓친 측정 알림 한 줄에서 진료 리포트에 필요한 것만 (잴 때 · 알림 시각)
public interface MissedReminder {
    MeasureSlot getSlot();

    Instant getScheduledAt();
}
