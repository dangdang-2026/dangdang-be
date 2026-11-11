package com.dangdang.domain.reminder.service;

import com.dangdang.domain.push.dto.response.PushDeliveryResponse;
import com.dangdang.domain.push.service.PushSendService;
import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.domain.reminder.enums.ReminderStatus;
import com.dangdang.domain.reminder.repository.ReminderRepository;
import com.dangdang.domain.user.entity.NotificationPreference;
import com.dangdang.domain.user.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// @Transactional을 일부러 안 붙임 — 푸시 서버를 기다리는 동안 DB 연결을 붙잡지 않게
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderDispatchService {

    static final Duration RETRY_WINDOW = Duration.ofMinutes(5);   // 푸시 서버가 잠깐 실패하면 5분까지 다시 시도
    static final Duration RENOTIFY_AFTER = Duration.ofMinutes(10);  // 보내고 10분 뒤 한 번 더
    static final Duration RENOTIFY_MAX_AGE = Duration.ofHours(1);   // 1시간 넘게 지난 알림은 다시 울리지 않는다
    private static final Limit BATCH = Limit.of(100);               // 한 바퀴에 100개까지

    private final ReminderRepository reminderRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final PushSendService pushSendService;
    private final ReminderDeliveryRecorder recorder;
    private final Clock clock;

    public void dispatchDue() {
        Instant now = clock.instant();
        for (Reminder reminder : reminderRepository.findDue(ReminderStatus.SCHEDULED, now, BATCH)) {
            try {
                dispatch(reminder, now);
            } catch (Exception e) {
                // 한 사람 것이 실패해도 다음 사람 알림은 계속 보낸다
                log.warn("[알림] {} 처리 실패: {}", reminder.getId(), e.getClass().getSimpleName());
            }
        }
    }

    public void renotifyDue() {
        Instant now = clock.instant();
        for (Reminder reminder : reminderRepository.findRenotifyDue(
                now.minus(RENOTIFY_AFTER), now.minus(RENOTIFY_MAX_AGE), BATCH)) {
            try {
                PushDeliveryResponse result = pushSendService.sendToUser(
                        reminder.getUser().getId(), ReminderMessages.renotify(reminder));
                recorder.recordRenotified(reminder.getId(), now, result.sentCount() > 0);
            } catch (Exception e) {
                log.warn("[알림] {} 재알림 실패: {}", reminder.getId(), e.getClass().getSimpleName());
            }
        }
    }

    private void dispatch(Reminder reminder, Instant now) {
        Long userId = reminder.getUser().getId();
        if (!measurementEnabled(userId)) {
            recorder.recordTurnedOff(reminder.getId());
            return;
        }
        PushDeliveryResponse result = pushSendService.sendToUser(userId, ReminderMessages.push(reminder));
        if (result.sentCount() > 0) {
            recorder.recordSent(reminder.getId(), now);
            return;
        }
        if (result.failedCount() > 0 && now.isBefore(reminder.dueAt().plus(RETRY_WINDOW))) {
            return;                              // SCHEDULED로 두면 다음 바퀴에 다시 시도한다
        }
        recorder.recordNoDevice(reminder.getId(), now);
    }

    // 설정 줄이 없으면 켜진 것으로 (DDL 기본값 true와 같게)
    private boolean measurementEnabled(Long userId) {
        return notificationPreferenceRepository.findByUserId(userId)
                .map(NotificationPreference::isMeasurementEnabled)
                .orElse(true);
    }
}
