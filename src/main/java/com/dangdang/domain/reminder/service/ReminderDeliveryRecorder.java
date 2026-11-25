package com.dangdang.domain.reminder.service;

import com.dangdang.domain.meal.entity.Meal;
import com.dangdang.domain.meal.repository.MealRepository;
import com.dangdang.domain.notification.entity.Notification;
import com.dangdang.domain.notification.service.NotificationService;
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
    private final NotificationService notificationService;
    private final MealRepository mealRepository;

    // 보내기 직전 「내가 보낸다」 표시 — false면 다른 서버가 보내는 중이거나 이미 끝난 알림
    @Transactional
    public boolean claimDue(Long reminderId, Instant now, Instant until) {
        return reminderRepository.claimDue(reminderId, now, until) == 1;
    }

    @Transactional
    public boolean claimRenotify(Long reminderId, Instant now, Instant until) {
        return reminderRepository.claimRenotify(reminderId, now, until) == 1;
    }

    // 다음 바퀴에 다시 시도할 수 있게 표시를 푼다
    @Transactional
    public void release(Long reminderId) {
        reminderRepository.release(reminderId);
    }

    // 줄을 잠가서 읽고, 실제로 SENT로 바꿨을 때만 알림함에 남김
    // (푸시를 보내는 사이 혈당을 쟀거나 끼니를 건너뛰었으면 그 상태를 지키고 알림함 줄도 안 만듦)
    @Transactional
    public void recordSent(Long reminderId, Instant now) {
        Reminder reminder = reminderRepository.findByIdForUpdate(reminderId).orElseThrow();
        if (reminder.markSent(now)) {
            saveInbox(reminder, false, now);
        }
    }

    // 받을 기기가 없을 때 — 푸시는 못 보냈어도 알림함에서는 보이게 남긴다
    @Transactional
    public void recordNoDevice(Long reminderId, Instant now) {
        Reminder reminder = reminderRepository.findByIdForUpdate(reminderId).orElseThrow();
        if (reminder.markSkipped()) {   // recordSent와 같은 이유로 실제로 바꿨을 때만
            saveInbox(reminder, false, now);
        }
    }

    // 측정 알림을 끈 사람 — 알림함에도 남기지 않는다
    @Transactional
    public void recordTurnedOff(Long reminderId) {
        reminderRepository.findByIdForUpdate(reminderId).orElseThrow().markSkipped();
    }

    // 재알림은 1회만 — 보내지 못했어도 다시 시도하지 않는다. 진짜 보낸 때만 알림함에 남긴다
    @Transactional
    public void recordRenotified(Long reminderId, Instant now, boolean delivered) {
        // 줄을 잠가서 읽음 — 옛 상태로 줄 전체를 덮어 그사이 바뀐 DISMISSED·DONE을 되돌리지 않게
        Reminder reminder = reminderRepository.findByIdForUpdate(reminderId).orElseThrow();
        reminder.markRenotified();
        if (delivered) {
            saveInbox(reminder, true, now);

        }
    }

    // 알림함 한 줄 — 제목은 첫 알림이면 「점심 식후 1시간, 잴 시간이에요」, 재알림이면 재알림 문구. 내용은 「12:12에 식사를 시작했어요」
    private void saveInbox(Reminder reminder, boolean renotify, Instant now) {
        Instant mealStartedAt = mealStartedAt(reminder);
        String title = renotify ? ReminderMessages.RENOTIFY_TITLE : ReminderMessages.inboxTitle(reminder, mealStartedAt);
        notificationService.record(Notification.measure(reminder.getUser(), reminder.getId(),
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
