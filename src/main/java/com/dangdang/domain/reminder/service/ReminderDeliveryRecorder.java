package com.dangdang.domain.reminder.service;

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

    @Transactional
    public void recordSent(Long reminderId, Instant now) {
        Reminder reminder = reminderRepository.findById(reminderId).orElseThrow();
        reminder.markSent(now);
        saveInbox(reminder, now);
    }

    // 받을 기기가 없을 때 — 푸시는 못 보냈어도 알림함에서는 보이게 남긴다
    @Transactional
    public void recordNoDevice(Long reminderId, Instant now) {
        Reminder reminder = reminderRepository.findById(reminderId).orElseThrow();
        reminder.markSkipped();
        saveInbox(reminder, now);
    }

    // 측정 알림을 끈 사람 — 알림함에도 남기지 않는다
    @Transactional
    public void recordTurnedOff(Long reminderId) {
        reminderRepository.findById(reminderId).orElseThrow().markSkipped();
    }

    private void saveInbox(Reminder reminder, Instant now) {
        notificationRepository.save(Notification.measure(reminder.getUser(), reminder.getId(),
                ReminderMessages.INBOX_TITLE, ReminderMessages.inboxBody(reminder),
                ReminderMessages.deepLink(reminder), now));
    }
}
