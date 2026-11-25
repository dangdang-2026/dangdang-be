package com.dangdang.domain.visit.service;

import com.dangdang.domain.caresite.entity.CareSite;
import com.dangdang.domain.caresite.repository.CareSiteRepository;
import com.dangdang.domain.notification.enums.NotificationType;
import com.dangdang.domain.push.infra.PushMessage;
import com.dangdang.domain.push.service.PushSendService;
import com.dangdang.domain.user.entity.NotificationPreference;
import com.dangdang.domain.user.repository.NotificationPreferenceRepository;
import com.dangdang.domain.visit.entity.Visit;
import com.dangdang.domain.visit.repository.VisitRepository;
import com.dangdang.global.common.KoreanTime;
import com.dangdang.global.config.ClockConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// 진료 전날·당일 알림 — 한국 시간 전날 저녁 7~10시 · 당일 아침 7시~밤 10시, 30초마다 도는 스케줄러가 부름
// 시간대는 산후 검사 알림과 같게 맞춤. 일정마다 켜고 끄는 설정은 없고 「진료·검사 알림」(scheduleEnabled)만 따름
// @Transactional을 일부러 안 붙임 — 한 건씩 recorder에서 따로 커밋함 (산후 검사 알림과 같은 방식)
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitReminderNotifier {

    static final LocalTime DAY_BEFORE_FROM = LocalTime.of(19, 0);   // 전날 저녁 — 준비물·금식을 챙길 시간
    static final LocalTime DAY_BEFORE_UNTIL = LocalTime.of(22, 0);  // 밤에는 울리지 않게 (지나면 전날 알림은 건너뜀)
    static final LocalTime SAME_DAY_FROM = LocalTime.of(7, 0);      // 당일 아침 (오전 진료도 챙길 수 있게)
    static final LocalTime SAME_DAY_UNTIL = LocalTime.of(22, 0);    // 밤에는 울리지 않게

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN);   // 「오전 10:00」

    private final VisitRepository visitRepository;
    private final CareSiteRepository careSiteRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final PushSendService pushSendService;
    private final VisitReminderRecorder recorder;
    private final Clock clock;

    public int notifyDue() {
        Instant now = clock.instant();
        ZonedDateTime local = now.atZone(ClockConfig.KST);
        LocalTime time = local.toLocalTime();
        LocalDate today = local.toLocalDate();
        int sent = 0;
        if (!time.isBefore(DAY_BEFORE_FROM) && time.isBefore(DAY_BEFORE_UNTIL)) {
            LocalDate tomorrow = today.plusDays(1);
            for (Visit visit : visitRepository.findDayBeforeDue(KoreanTime.startOfDay(tomorrow),
                    KoreanTime.endOfDay(tomorrow))) {
                sent += handle(visit, true, now);
            }
        }
        if (!time.isBefore(SAME_DAY_FROM) && time.isBefore(SAME_DAY_UNTIL)) {
            for (Visit visit : visitRepository.findSameDayDue(now, KoreanTime.endOfDay(today))) {
                sent += handle(visit, false, now);
            }
        }
        return sent;
    }

    private int handle(Visit visit, boolean dayBefore, Instant now) {
        try {
            // 끈 사람, 기기 없는 사람도 처리한 것으로 (다시 안 봄). 그사이 날짜가 바뀌었거나 지워졌으면 안 보냄
            if (!recorder.claim(visit.getId(), visit.getVisitAt(), dayBefore, now)) {
                return 0;
            }
            return send(visit, dayBefore, now);
        } catch (Exception e) {
            // 한 사람 것이 실패해도 다음 사람 알림은 계속 보냄
            log.warn("[진료] 알림 {} 처리 실패: {}", visit.getId(), e.getClass().getSimpleName());
            return 0;
        }
    }

    private int send(Visit visit, boolean dayBefore, Instant now) {
        Long userId = visit.getUser().getId();
        boolean enabled = notificationPreferenceRepository.findByUserId(userId)
                .map(NotificationPreference::isScheduleEnabled).orElse(true);   // 「진료·검사 알림」 설정 (M-04)
        if (!enabled) {
            return 0;
        }
        NotificationType type = dayBefore ? NotificationType.VISIT_BEFORE : NotificationType.VISIT_TODAY;
        String title = (dayBefore ? "내일" : "오늘") + " 진료가 있어요";
        String body = body(visit, userId);
        String deepLink = "/visits/" + visit.getId();
        recorder.saveInbox(userId, type, title, body, deepLink, now);   // 푸시가 실패해도 알림함에는 보이게 먼저 남김
        // 병원 이름·시각은 내용(body)에만 → 미리보기 숨김이면 PushSendService가 가림
        pushSendService.sendToUser(userId, new PushMessage("당당", title + " · " + body, deepLink, null));
        log.info("[진료] {} 알림 - 사용자: {}, 일정: {}", type, userId, visit.getId());
        return 1;
    }

    // 「당당산부인과 · 오전 10:00」 (병원이 없거나 지운 병원이면 시각만)
    private String body(Visit visit, Long userId) {
        String at = visit.getVisitAt().atZone(ClockConfig.KST).format(TIME);
        if (visit.getCareSiteId() == null) {
            return at;
        }
        return careSiteRepository.findByIdAndUserIdAndDeletedAtIsNull(visit.getCareSiteId(), userId)
                .map(CareSite::getName)
                .map(name -> name + " · " + at)
                .orElse(at);
    }
}
