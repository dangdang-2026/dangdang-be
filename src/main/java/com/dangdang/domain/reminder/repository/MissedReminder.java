package com.dangdang.domain.reminder.repository;

import com.dangdang.domain.criteria.enums.MeasureSlot;

import java.time.Instant;

// 놓친 측정 알림 한 줄에서 진료 리포트에 필요한 것만 (잴 때 · 알림 시각 · 식사 알림이면 식사 시작 시각)
public interface MissedReminder {
    MeasureSlot getSlot();

    Instant getScheduledAt();

    Instant getMealStartedAt();   // 식사 없는 알림(공복·자기 전)이면 null

    // 빈칸 날짜를 정하는 시각: 식사 알림은 식사 시작, 아니면 알림 시각
    default Instant cellAt() {
        return getMealStartedAt() != null ? getMealStartedAt() : getScheduledAt();
    }
}
