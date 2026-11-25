package com.dangdang.domain.visit.service;

import com.dangdang.domain.push.infra.PushMessage;
import com.dangdang.domain.push.service.PushSendService;
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

// 진료 전날·당일 알림 — 한국 시간 전날 저녁 7~10시 · 당일 아침 7시~밤 10시, 30초마다 도는 스케줄러가 부름
// 시간대는 산후 검사 알림과 같게 맞춤. 일정마다 켜고 끄는 설정은 없고 「진료·검사 알림」(scheduleEnabled)만 따름
// 알고 있는 한계: 밤 10시 넘어 등록한 다음 날 새벽(0~7시) 진료는 두 시간대 모두 벗어나 알림이 없음
// @Transactional을 일부러 안 붙임 — 한 건씩 recorder에서 따로 커밋하고, 푸시는 커밋 뒤에 보냄
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitReminderNotifier {

    static final LocalTime DAY_BEFORE_FROM = LocalTime.of(19, 0);   // 전날 저녁 — 준비물·금식을 챙길 시간
    static final LocalTime DAY_BEFORE_UNTIL = LocalTime.of(22, 0);  // 밤에는 울리지 않게 (지나면 전날 알림은 건너뜀)
    static final LocalTime SAME_DAY_FROM = LocalTime.of(7, 0);      // 당일 아침 (오전 진료도 챙길 수 있게)
    static final LocalTime SAME_DAY_UNTIL = LocalTime.of(22, 0);    // 밤에는 울리지 않게

    private final VisitRepository visitRepository;
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
        Long userId = visit.getUser().getId();
        try {
            PushMessage push = recorder.claimAndRecord(visit.getId(), visit.getVisitAt(), dayBefore, now);
            if (push == null) {
                return 0;
            }
            // 알림함은 이미 커밋됨 → 푸시가 실패해도 알림함에는 보이고, 같은 알림을 다시 보내지는 않음
            pushSendService.sendToUser(userId, push);
            log.info("[진료] {} 알림 - 사용자: {}, 일정: {}", dayBefore ? "전날" : "당일", userId, visit.getId());
            return 1;
        } catch (Exception e) {
            // 한 사람 것이 실패해도 다음 사람 알림은 계속 보냄
            log.warn("[진료] 알림 {} 처리 실패: {}", visit.getId(), e.getClass().getSimpleName(), e);
            return 0;
        }
    }
}
