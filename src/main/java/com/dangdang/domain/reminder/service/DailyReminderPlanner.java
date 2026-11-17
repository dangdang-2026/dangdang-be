package com.dangdang.domain.reminder.service;

import com.dangdang.domain.criteria.dto.response.CriteriaVersionResponse;
import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.criteria.service.CriteriaService;
import com.dangdang.domain.pregnancy.service.MeasurementPause;
import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.domain.reminder.repository.ReminderRepository;
import com.dangdang.domain.user.entity.NotificationPreference;
import com.dangdang.domain.user.repository.NotificationPreferenceRepository;
import com.dangdang.global.config.ClockConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

// 공복·자기 전 측정 알림 (A-02d·M-19) — 식사가 없어서 식후 알림처럼 만들 수 없다.
// 알림 시각이 되면 reminders에 한 줄 만들고, 보내기·재알림·열람·혈당 저장 끝 처리는 식후 알림 흐름을 그대로 탄다.
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReminderPlanner {

    // 서버가 잠깐 꺼졌다 켜져도 이 시간 안이면 늦게라도 만든다. 그보다 지났으면 오늘은 건너뛴다 (아침 7시 알림이 오후에 오면 이상하니까)
    static final Duration CATCH_UP = Duration.ofMinutes(30);

    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final ReminderRepository reminderRepository;
    private final CriteriaService criteriaService;
    private final MeasurementPause measurementPause;
    private final Clock clock;

    @Transactional
    public int planDue() {
        Instant now = clock.instant();
        ZonedDateTime local = now.atZone(ClockConfig.KST);   // 알림 시각은 한국 시간이다 (시계의 시간대와 상관없이)
        int created = 0;
        for (NotificationPreference pref : notificationPreferenceRepository.findAllMeasurementEnabled()) {
            created += planOne(pref, MeasureSlot.FASTING, pref.getFastingAlarmTime(), local, now);
            created += planOne(pref, MeasureSlot.BEDTIME, pref.getBedtimeAlarmTime(), local, now);
        }
        if (created > 0) {
            log.info("[알림] 공복·자기 전 알림 {}개 만듦", created);
        }
        return created;
    }

    private int planOne(NotificationPreference pref, MeasureSlot slot, LocalTime alarmTime,
                        ZonedDateTime local, Instant now) {
        Instant alarmAt = todayAt(local.toLocalDate(), alarmTime);
        if (now.isBefore(alarmAt) || !now.isBefore(alarmAt.plus(CATCH_UP))) {
            return 0;                                  // 아직 시간이 안 됐거나, 너무 지났다 (시간 비교는 가볍게 먼저)
        }
        Long userId = pref.getUser().getId();
        if (reminderRepository.existsByUserIdAndSlotAndScheduledAt(userId, slot, alarmAt)) {
            return 0;                                  // 오늘 이미 만들었다
        }
        if (!hospitalSaysMeasure(userId, slot) || !measurementPause.allowsMeasurement(userId)) {
            return 0;
        }
        reminderRepository.save(Reminder.scheduled(pref.getUser(), null, slot, alarmAt, now));
        return 1;
    }

    // 병원 기준 재는 때에 이 시점이 있을 때만 (A-02b — 병원에서 들은 것만)
    private boolean hospitalSaysMeasure(Long userId, MeasureSlot slot) {
        CriteriaVersionResponse criteria = criteriaService.current(userId);
        return criteria != null && criteria.measureSlots() != null && criteria.measureSlots().contains(slot);
    }

    private Instant todayAt(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(ClockConfig.KST).toInstant();
    }
}
