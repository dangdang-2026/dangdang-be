package com.dangdang.domain.postpartum.service;

import com.dangdang.domain.notification.entity.Notification;
import com.dangdang.domain.notification.repository.NotificationRepository;
import com.dangdang.domain.postpartum.entity.PostpartumTest;
import com.dangdang.domain.postpartum.repository.PostpartumTestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

// 검사 알림 결과를 DB에 적는 일만 한다 — 한 건씩 따로 커밋해서, 한 사람이 실패해도 다른 사람 처리가 되돌아가지 않게
@Service
@RequiredArgsConstructor
public class PostpartumTestRecorder {

    private final PostpartumTestRepository postpartumTestRepository;
    private final NotificationRepository notificationRepository;

    // 보내기 전에 먼저 「처리함」으로 적는다 → 푸시가 실패해도 같은 알림이 30초마다 다시 가지 않는다
    @Transactional
    public void markHandled(Long testId, boolean dayBefore, Instant now) {
        PostpartumTest test = postpartumTestRepository.findById(testId).orElseThrow();
        if (dayBefore) {
            test.markDayBeforeHandled(now);
        } else {
            test.markSameDayHandled(now);
        }
    }

    // 알림함 한 줄 (종류 POSTPARTUM)
    @Transactional
    public void saveInbox(Long testId, String title, String deepLink, Instant now) {
        PostpartumTest test = postpartumTestRepository.findById(testId).orElseThrow();
        notificationRepository.save(Notification.postpartum(test.getUser(), title, deepLink, now));
    }
}
