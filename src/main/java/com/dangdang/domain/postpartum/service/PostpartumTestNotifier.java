package com.dangdang.domain.postpartum.service;

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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// 산후 검사 전날과 당일 알림 (PP-04 · E-08) — 한국 시간 전날 저녁 7~10시 · 당일 아침 7시~밤 10시, 30초마다 도는 스케줄러가 부른다
// 검사 날짜가 딱 내일·오늘인 것만 찾으니 지난 날짜로 적은 검사는 알림이 가지 않는다
// @Transactional을 일부러 안 붙임 — 한 건씩 recorder에서 따로 커밋하고, 푸시(외부 호출)는 트랜잭션 밖에서 보낸다
@Slf4j
@Service
@RequiredArgsConstructor
public class PostpartumTestNotifier {

    static final LocalTime DAY_BEFORE_FROM = LocalTime.of(19, 0);   // 전날 저녁 (E-08 「전날 저녁」) — 금식 같은 준비를 챙기라는 뜻
    static final LocalTime DAY_BEFORE_UNTIL = LocalTime.of(22, 0);  // 밤에는 울리지 않게 (지나면 전날 알림은 건너뛰고 당일 알림만)
    static final LocalTime SAME_DAY_FROM = LocalTime.of(7, 0);      // 당일 아침 (공복 검사가 보통 오전 일찍이라 9시는 늦음)
    static final LocalTime SAME_DAY_UNTIL = LocalTime.of(22, 0);    // 아침에 못 받은 사람은 낮에라도 받게 두되, 밤에는 울리지 않게
    static final String PREPARE = "병원에서 들은 준비 사항(금식 등)도 확인해 주세요";
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M월 d일(E)", Locale.KOREAN);

    private final PostpartumTestRepository postpartumTestRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final PushSendService pushSendService;
    private final PostpartumTestRecorder recorder;
    private final Clock clock;

    public int notifyDue() {
        Instant now = clock.instant();
        ZonedDateTime local = now.atZone(ClockConfig.KST);
        LocalTime time = local.toLocalTime();
        int sent = 0;
        if (!time.isBefore(DAY_BEFORE_FROM) && time.isBefore(DAY_BEFORE_UNTIL)) {
            for (PostpartumTest test : postpartumTestRepository.findDayBeforeDue(local.toLocalDate().plusDays(1))) {
                sent += handle(test, true, now);
            }
        }
        if (!time.isBefore(SAME_DAY_FROM) && time.isBefore(SAME_DAY_UNTIL)) {
            for (PostpartumTest test : postpartumTestRepository.findSameDayDue(local.toLocalDate())) {
                sent += handle(test, false, now);
            }
        }
        return sent;
    }

    // 「당화혈색소」 → 「당화혈색소 검사」, 「75g 경구 당부하 검사」 → 그대로 (「검사 검사」 막기, H-15)
    static String testName(PostpartumTest test) {
        String name = test.getName().strip();
        return name.endsWith("검사") ? name : name + " 검사";
    }

    // 푸시는 그 순간 보는 것 → 「내일·오늘」. 전날엔 준비 사항 한 문장 (E-08). 장소는 안 넣는다 (잠금화면에 병원 이름이 뜨지 않게)
    static String pushTitle(PostpartumTest test, boolean dayBefore) {
        return (dayBefore ? "내일 " : "오늘 ") + testName(test) + "가 있어요" + (dayBefore ? ". " + PREPARE : "");
    }

    // 알림함은 보관되니까 「오늘·내일」 대신 검사 날짜 (H-15 「보관된 알림은 절대 날짜」)
    static String inboxTitle(PostpartumTest test, boolean dayBefore) {
        return MONTH_DAY.format(test.getTestDate()) + " " + testName(test) + "가 있어요"
                + (dayBefore ? ". " + PREPARE : "");
    }

    private int handle(PostpartumTest test, boolean dayBefore, Instant now) {
        try {
            // 끈 사람, 기기 없는 사람도 처리한 것으로 (다시 안 봄). 이미 누가 잡았거나 그사이 조건이 바뀌었으면 안 보냄
            if (!recorder.claim(test.getId(), test.getTestDate(), dayBefore, now)) {
                return 0;
            }
            return send(test, dayBefore, now);
        } catch (Exception e) {
            // 한 사람 것이 실패해도 다음 사람 알림은 계속 보낸다
            log.warn("[산후] 검사 알림 {} 처리 실패: {}", test.getId(), e.getClass().getSimpleName());
            return 0;
        }
    }

    private int send(PostpartumTest test, boolean dayBefore, Instant now) {
        Long userId = test.getUser().getId();
        boolean enabled = notificationPreferenceRepository.findByUserId(userId)
                .map(NotificationPreference::isScheduleEnabled).orElse(true);   // 「진료·검사 알림」 설정 (M-04)
        if (!enabled) {
            return 0;
        }
        String deepLink = "/postpartum/tests/" + test.getId();
        recorder.saveInbox(test.getId(), inboxTitle(test, dayBefore), deepLink, now);   // 푸시가 실패해도 알림함에는 보이게 먼저 남긴다
        pushSendService.sendToUser(userId, new PushMessage("당당", pushTitle(test, dayBefore), deepLink, null));
        log.info("[산후] 검사 알림 - 사용자: {}, 검사: {}", userId, test.getId());
        return 1;
    }
}
