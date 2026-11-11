package com.dangdang.domain.reminder.service;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.push.infra.PushMessage;
import com.dangdang.domain.reminder.entity.Reminder;

// 측정 알림 문구를 한곳에 모은다 (수치·판단 단어는 넣지 않는다)
final class ReminderMessages {

    static final String INBOX_TITLE = "혈당 잴 시간이에요";
    static final String RENOTIFY_TITLE = "아직 혈당을 안 쟀다면 지금 재 주세요";

    private ReminderMessages() {
    }

    static PushMessage push(Reminder reminder) {
        return new PushMessage("당당", INBOX_TITLE, deepLink(reminder), reminder.getId());
    }

    static PushMessage renotify(Reminder reminder) {
        return new PushMessage("당당", RENOTIFY_TITLE, deepLink(reminder), reminder.getId());
    }

    static String inboxBody(Reminder reminder) {
        return slotName(reminder.getSlot()) + " 혈당을 재 주세요";
    }

    static String deepLink(Reminder reminder) {
        return "/today?sheet=glucose&reminderId=" + reminder.getId();
    }

    private static String slotName(MeasureSlot slot) {
        return switch (slot) {
            case FASTING -> "공복";
            case BREAKFAST_AFTER -> "아침 식후";
            case LUNCH_AFTER -> "점심 식후";
            case DINNER_AFTER -> "저녁 식후";
            case BEDTIME -> "자기 전";
        };
    }
}
