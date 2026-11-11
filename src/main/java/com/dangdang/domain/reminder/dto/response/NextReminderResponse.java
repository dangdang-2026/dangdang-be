package com.dangdang.domain.reminder.dto.response;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.domain.reminder.enums.ReminderStatus;

import java.time.Instant;


public record NextReminderResponse(Long reminderId, MeasureSlot slot, Instant scheduledAt, ReminderStatus status) {

    public static NextReminderResponse from(Reminder r) {
        return new NextReminderResponse(r.getId(), r.getSlot(), r.dueAt(), r.getStatus());
    }
}
