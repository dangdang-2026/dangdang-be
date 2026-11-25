package com.dangdang.domain.postpartum.service;

import com.dangdang.domain.notification.entity.Notification;
import com.dangdang.domain.notification.service.NotificationService;
import com.dangdang.domain.postpartum.entity.PostpartumTest;
import com.dangdang.domain.postpartum.repository.PostpartumTestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

// 검사 알림 결과를 DB에 적는 일만 한다 — 한 건씩 따로 커밋해서, 한 사람이 실패해도 다른 사람 처리가 되돌아가지 않게
@Service
@RequiredArgsConstructor
public class PostpartumTestRecorder {

    private final PostpartumTestRepository postpartumTestRepository;
    private final NotificationService notificationService;

    // 보내기 전에 먼저 「처리함」으로 적는다 → 푸시가 실패해도 같은 알림이 30초마다 다시 가지 않는다
    // 조건부 UPDATE 한 문장이라 둘이 같이 잡아도 한쪽만 true (같은 알림 두 번 방지). false면 보내지 않는다
    @Transactional
    public boolean claim(Long testId, LocalDate testDate, boolean dayBefore, Instant now) {
        int updated = dayBefore
                ? postpartumTestRepository.claimDayBefore(testId, testDate, now)
                : postpartumTestRepository.claimSameDay(testId, testDate, now);
        return updated == 1;
    }

    // 알림함 한 줄 (종류 POSTPARTUM)
    @Transactional
    public void saveInbox(Long testId, String title, String deepLink, Instant now) {
        PostpartumTest test = postpartumTestRepository.findById(testId).orElseThrow();
        notificationService.record(Notification.postpartum(test.getUser(), title, deepLink, now));
    }
}
