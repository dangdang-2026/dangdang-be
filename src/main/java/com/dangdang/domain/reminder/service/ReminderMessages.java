package com.dangdang.domain.reminder.service;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.push.infra.PushMessage;
import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.global.config.ClockConfig;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

// 측정 알림 문구를 한곳에 모은다 (수치·판단 단어는 넣지 않는다)
//mealStartedAt = 식사 시작 시각, 식사가 없는 알림(공복·자기 전)이면 null
final class ReminderMessages {

    static final String RENOTIFY_TITLE = "아직 혈당을 안 쟀다면 지금 재 주세요";
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private ReminderMessages() {
    }

    // 푸시 (E-05) — 예: 점심 식후 1시간 혈당 잴 시간이에요
    static PushMessage push(Reminder reminder, Instant mealStartedAt) {
        return new PushMessage("당당", pushText(reminder, mealStartedAt), deepLink(reminder), reminder.getId());
    }

    static PushMessage renotify(Reminder reminder) {
        return new PushMessage("당당", RENOTIFY_TITLE, deepLink(reminder), reminder.getId());
    }

    static String pushText(Reminder reminder, Instant mealStartedAt) {
        String wait = waitText(reminder, mealStartedAt);
        return wait == null
                ? slotName(reminder.getSlot()) + " 혈당을 잴 시간이에요"
                : slotName(reminder.getSlot()) + " " + wait + " 혈당 잴 시간이에요";
    }

    // 알림함 제목 (H-15) — 예: 점심 식후 1시간, 잴 시간이에요 / 공복 혈당을 잴 시간이에요
    static String inboxTitle(Reminder reminder, Instant mealStartedAt) {
        String wait = waitText(reminder, mealStartedAt);
        return wait == null
                ? slotName(reminder.getSlot()) + " 혈당을 잴 시간이에요"
                : slotName(reminder.getSlot()) + " " + wait + ", 잴 시간이에요";
    }

    // 알림함 내용 (H-15) — 예: 12:12에 식사를 시작했어요. 식사가 없으면 내용 없음(null)
    static String inboxBody(Instant mealStartedAt) {
        if (mealStartedAt == null) {
            return null;
        }
        return HH_MM.format(mealStartedAt.atZone(ClockConfig.KST)) + "에 식사를 시작했어요";
    }

    static String deepLink(Reminder reminder) {
        return "/today?sheet=glucose&reminderId=" + reminder.getId();
    }

    // 식사 시작 → 원래 예정 시각까지: 60분 「1시간」, 90분 「1시간 30분」, 30분 「30분」. 식사가 없거나 0분 이하면 null
    private static String waitText(Reminder reminder, Instant mealStartedAt) {
        if (mealStartedAt == null) {
            return null;
        }
        long minutes = Duration.between(mealStartedAt, reminder.getScheduledAt()).toMinutes();
        if (minutes <= 0) {
            return null;
        }
        long hours = minutes / 60;
        long rest = minutes % 60;
        if (hours == 0) {
            return rest + "분";
        }
        return rest == 0 ? hours + "시간" : hours + "시간 " + rest + "분";
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
