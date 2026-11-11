package com.dangdang.domain.reminder.dto.response;

import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.domain.reminder.enums.ReminderStatus;

import java.time.Instant;

public record ReminderSnoozeResponse(Long id, ReminderStatus status, Instant scheduledAt, Instant snoozedUntil) {

    public static ReminderSnoozeResponse from(Reminder r) {
        return new ReminderSnoozeResponse(r.getId(), r.getStatus(), r.getScheduledAt(), r.getSnoozedUntil());
    }
}
