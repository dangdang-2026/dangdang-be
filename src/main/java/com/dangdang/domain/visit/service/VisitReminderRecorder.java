package com.dangdang.domain.visit.service;

import com.dangdang.domain.caresite.entity.CareSite;
import com.dangdang.domain.caresite.repository.CareSiteRepository;
import com.dangdang.domain.notification.entity.Notification;
import com.dangdang.domain.notification.enums.NotificationType;
import com.dangdang.domain.notification.service.NotificationService;
import com.dangdang.domain.push.infra.PushMessage;
import com.dangdang.domain.user.entity.NotificationPreference;
import com.dangdang.domain.user.repository.NotificationPreferenceRepository;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.domain.visit.entity.Visit;
import com.dangdang.domain.visit.repository.VisitRepository;
import com.dangdang.global.config.ClockConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// 진료 알림 한 건을 DB에 적는 일 — 한 건씩 따로 커밋해서, 한 사람이 실패해도 다른 사람 처리가 되돌아가지 않게
@Service
@RequiredArgsConstructor
public class VisitReminderRecorder {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN);   // 「오전 10:00」
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M월 d일(E)", Locale.KOREAN);   // 「11월 10일(화)」

    private final VisitRepository visitRepository;
    private final UserRepository userRepository;
    private final CareSiteRepository careSiteRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final NotificationService notificationService;

    // 「처리함」 적기 + 알림함 한 줄을 한 트랜잭션으로. 보낼 푸시를 돌려줌 (안 보낼 거면 null)
    // - 조건부 UPDATE가 줄을 잠근 채로 커밋까지 가서, 그사이 일정 수정·삭제는 기다림 → 문구는 처리한 그 상태로 만듦
    // - 알림함까지 같이 커밋해서, 중간에 실패하면 「처리함」도 되돌아가 다음 바퀴에 다시 시도함
    // - 읽은 뒤에 날짜가 바뀌었거나 지워졌거나 이미 처리했으면 null (안 보냄)
    // - 「진료·검사 알림」을 끈 사람은 처리함으로만 적고 null (다시 안 봄)
    @Transactional
    public PushMessage claimAndRecord(Long userId, Long visitId, Instant visitAt, boolean dayBefore, Instant now) {
        // 회원 줄을 먼저 잡음 (탈퇴와 같은 순서: 회원 → 일정). 이미 탈퇴했으면 안 보냄
        if (userRepository.lockForKeyShare(userId).isEmpty()) {
            return null;
        }
        int claimed = dayBefore
                ? visitRepository.claimDayBefore(visitId, visitAt, now)
                : visitRepository.claimSameDay(visitId, visitAt, now);
        if (claimed != 1) {
            return null;
        }
        Visit visit = visitRepository.findById(visitId).orElseThrow();   // 잠근 줄이라 방금 확인한 그 상태
        boolean enabled = notificationPreferenceRepository.findByUserId(userId)
                .map(NotificationPreference::isScheduleEnabled).orElse(true);   // 「진료·검사 알림」 설정 (M-04)
        if (!enabled) {
            return null;
        }
        NotificationType type = dayBefore ? NotificationType.VISIT_BEFORE : NotificationType.VISIT_TODAY;
        String at = visit.getVisitAt().atZone(ClockConfig.KST).format(TIME);
        String deepLink = "/visits/" + visitId;
        // 알림함은 보관되니까 「내일·오늘」 대신 진료 날짜 (H-15 「보관된 알림은 절대 날짜」, 산후 검사 알림과 같음)
        notificationService.record(Notification.visit(visit.getUser(), type, inboxTitle(visit), body(visit, userId, at),
                deepLink, now));
        // 푸시는 받는 순간 보니까 「내일·오늘」. 잠금화면에 병원 이름이 뜨지 않게 시각까지만 (산후 검사 알림과 같은 기준)
        return new PushMessage("당당", (dayBefore ? "내일" : "오늘") + " 진료가 있어요 · " + at, deepLink, null);
    }

    // 「11월 10일(화) 진료가 있어요」
    static String inboxTitle(Visit visit) {
        return visit.getVisitAt().atZone(ClockConfig.KST).format(MONTH_DAY) + " 진료가 있어요";
    }

    // 알림함 내용 「당당산부인과 · 오전 10:00」 (병원이 없거나 지운 병원이면 시각만)
    private String body(Visit visit, Long userId, String at) {
        if (visit.getCareSiteId() == null) {
            return at;
        }
        return careSiteRepository.findByIdAndUserIdAndDeletedAtIsNull(visit.getCareSiteId(), userId)
                .map(CareSite::getName)
                .map(name -> name + " · " + at)
                .orElse(at);
    }
}
