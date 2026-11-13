package com.dangdang.domain.reminder.service;

import com.dangdang.domain.meal.entity.Meal;
import com.dangdang.domain.meal.repository.MealRepository;
import com.dangdang.domain.notification.entity.Notification;
import com.dangdang.domain.notification.repository.NotificationRepository;
import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.domain.reminder.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

// 발송 결과를 DB에 적는 일만 한다 — 푸시를 기다리는 동안 트랜잭션을 잡지 않으려고 따로 뺐다
@Service
@RequiredArgsConstructor
public class ReminderDeliveryRecorder {

    private final ReminderRepository reminderRepository;
    private final NotificationRepository notificationRepository;
    private final MealRepository mealRepository;

    @Transactional
    public void recordSent(Long reminderId, Instant now) {
        Reminder reminder = reminderRepository.findById(reminderId).orElseThrow();
        reminder.markSent(now);
        saveInbox(reminder,false, now);
    }

    // 받을 기기가 없을 때 — 푸시는 못 보냈어도 알림함에서는 보이게 남긴다
    @Transactional
    public void recordNoDevice(Long reminderId, Instant now) {
        Reminder reminder = reminderRepository.findById(reminderId).orElseThrow();
        reminder.markSkipped();
        saveInbox(reminder, false, now);
    }

    // 측정 알림을 끈 사람 — 알림함에도 남기지 않는다
    @Transactional
    public void recordTurnedOff(Long reminderId) {
        reminderRepository.findById(reminderId).orElseThrow().markSkipped();
    }

    // 재알림은 1회만 — 보내지 못했어도 다시 시도하지 않는다. 진짜 보낸 때만 알림함에 남긴다
    @Transactional
    public void recordRenotified(Long reminderId, Instant now, boolean delivered) {
        Reminder reminder = reminderRepository.findById(reminderId).orElseThrow();
        reminder.markRenotified();
        if (delivered) {
            saveInbox(reminder, true, now);

        }
    }

    // 알림함 한 줄 — 제목은 첫 알림이면 「점심 식후 1시간, 잴 시간이에요」, 재알림이면 재알림 문구. 내용은 「12:12에 식사를 시작했어요」
    private void saveInbox(Reminder reminder, boolean renotify, Instant now) {
        Instant mealStartedAt = mealStartedAt(reminder);
        String title = renotify ? ReminderMessages.RENOTIFY_TITLE : ReminderMessages.inboxTitle(reminder, mealStartedAt);
        notificationRepository.save(Notification.measure(reminder.getUser(), reminder.getId(),
                title, ReminderMessages.inboxBody(mealStartedAt),
                ReminderMessages.deepLink(reminder), now));
    }

    // 문구에 넣을 식사 시작 시각 — 식사가 없는 알림(공복·자기 전)이면 null
    private Instant mealStartedAt(Reminder reminder) {
        if (reminder.getMealId() == null) {
            return null;
        }
        return mealRepository.findById(reminder.getMealId()).map(Meal::getStartedAt).orElse(null);
    }
}
