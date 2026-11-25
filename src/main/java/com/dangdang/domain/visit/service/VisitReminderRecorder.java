package com.dangdang.domain.visit.service;

import com.dangdang.domain.notification.entity.Notification;
import com.dangdang.domain.notification.enums.NotificationType;
import com.dangdang.domain.notification.service.NotificationService;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.domain.visit.repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

// 진료 알림 결과를 DB에 적는 일만 함 — 한 건씩 따로 커밋해서, 한 사람이 실패해도 다른 사람 처리가 되돌아가지 않게
// (산후 검사 알림 PostpartumTestRecorder와 같은 방식)
@Service
@RequiredArgsConstructor
public class VisitReminderRecorder {

    private final VisitRepository visitRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // 보내기 전에 먼저 「처리함」으로 적음 → 푸시가 실패해도 같은 알림이 30초마다 다시 가지 않음
    // 조건부 UPDATE라서, 읽은 뒤에 날짜가 바뀌었거나 지워졌으면 false → 보내지 않음
    @Transactional
    public boolean claim(Long visitId, Instant visitAt, boolean dayBefore, Instant now) {
        int updated = dayBefore
                ? visitRepository.claimDayBefore(visitId, visitAt, now)
                : visitRepository.claimSameDay(visitId, visitAt, now);
        return updated == 1;
    }

    // 알림함 한 줄 (종류 VISIT_BEFORE · VISIT_TODAY)
    @Transactional
    public void saveInbox(Long userId, NotificationType type, String title, String body, String deepLink, Instant now) {
        notificationService.record(Notification.visit(userRepository.getReferenceById(userId), type, title, body,
                deepLink, now));
    }
}
