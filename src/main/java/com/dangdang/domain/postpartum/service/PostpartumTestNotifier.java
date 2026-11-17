package com.dangdang.domain.postpartum.service;

import com.dangdang.domain.notification.entity.Notification;
import com.dangdang.domain.notification.repository.NotificationRepository;
import com.dangdang.domain.postpartum.entity.PostpartumTest;
import com.dangdang.domain.postpartum.repository.PostpartumTestRepository;
import com.dangdang.domain.push.infra.PushMessage;
import com.dangdang.domain.push.service.PushSendService;
import com.dangdang.domain.user.entity.NotificationPreference;
import com.dangdang.domain.user.repository.NotificationPreferenceRepository;
import com.dangdang.global.config.ClockConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;

// 산후 검사 전날과 당일 알림 (PP-04) — 매일 한국 오전 9시부터, 30초마다 도는 스케줄러가 부른다
@Slf4j
@Service
@RequiredArgsConstructor
public class PostpartumTestNotifier {

    static final LocalTime SEND_FROM = LocalTime.of(9, 0);   // 밤·새벽에 울리지 않게

    private final PostpartumTestRepository postpartumTestRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final NotificationRepository notificationRepository;
    private final PushSendService pushSendService;
    private final Clock clock;

    @Transactional
    public int notifyDue() {
        Instant now = clock.instant();
        ZonedDateTime local = now.atZone(ClockConfig.KST);
        if (local.toLocalTime().isBefore(SEND_FROM)) {
            return 0;
        }
        int sent = 0;
        for (PostpartumTest test : postpartumTestRepository.findDayBeforeDue(local.toLocalDate().plusDays(1))) {
            test.markDayBeforeHandled(now);   // 끈 사람,기기 없는 사람도 처리한 것으로 (다시 안 봄)
            sent += send(test, "내일 " + test.getName() + " 검사가 있어요", now);
        }
        for (PostpartumTest test : postpartumTestRepository.findSameDayDue(local.toLocalDate())) {
            test.markSameDayHandled(now);
            sent += send(test, "오늘 " + test.getName() + " 검사가 있어요", now);
        }
        return sent;
    }

    private int send(PostpartumTest test, String title, Instant now) {
        Long userId = test.getUser().getId();
        boolean enabled = notificationPreferenceRepository.findByUserId(userId)
                .map(NotificationPreference::isScheduleEnabled).orElse(true);   // 「진료·검사 알림」 설정 (M-04)
        if (!enabled) {
            return 0;
        }
        String deepLink = "/postpartum/tests/" + test.getId();
        pushSendService.sendToUser(userId, new PushMessage("당당", title, deepLink, null));
        notificationRepository.save(Notification.postpartum(test.getUser(), title, deepLink, now));
        log.info("[산후] 검사 알림 - 사용자: {}, 검사: {}", userId, test.getId());
        return 1;
    }
}
