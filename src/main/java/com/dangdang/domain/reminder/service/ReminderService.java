package com.dangdang.domain.reminder.service;

import com.dangdang.domain.criteria.dto.response.CriteriaVersionResponse;
import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.criteria.service.CriteriaService;
import com.dangdang.domain.meal.enums.MealSlot;
import com.dangdang.domain.meal.repository.MealRepository;
import com.dangdang.domain.notification.repository.NotificationRepository;
import com.dangdang.domain.reminder.dto.response.ReminderOpenResponse;
import com.dangdang.domain.reminder.dto.response.ReminderSnoozeResponse;
import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.domain.reminder.enums.ReminderStatus;
import com.dangdang.domain.reminder.exception.ReminderErrorCode;
import com.dangdang.domain.reminder.repository.ReminderRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.config.ClockConfig;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReminderService {
    private static final Duration SNOOZE = Duration.ofMinutes(10);
    private final ReminderRepository reminderRepository;
    private final NotificationRepository notificationRepository;
    private final CriteriaService criteriaService;
    private final UserRepository userRepository;
    private final MealRepository mealRepository;
    private final Clock clock;
    private final Optional<LocalDemoDelay> demoDelay;   //local 프로필에서만 값이 있다

    @Transactional
    public Reminder scheduleAfterMeal(Long userId, Long mealId, MealSlot mealSlot, Instant startedAt) {
        MeasureSlot slot = afterSlotOf(mealSlot);
        if (slot == null) {
            return null;        //간식 잴 칸 없을 때
        }
        CriteriaVersionResponse criteria = criteriaService.current(userId);
        if (criteria == null || criteria.postMealMinutes() == null) {
            return null;        //앱이 시간을 정할 순 없음.
        }
        if (criteria.measureSlots() == null || !criteria.measureSlots().contains(slot)) {
            return null;        // 병원에서 재라고 한 때가 아닐 때
        }
        //local 시연이면 짧게, 아니면 병원 기준 식후 시간
        Duration wait = demoDelay.map(LocalDemoDelay::delay)
                .orElse(Duration.ofMinutes(criteria.postMealMinutes()));
        Instant scheduledAt = startedAt.plus(wait);
        Instant now = clock.instant();
        if (!scheduledAt.isAfter(now)) {
            return null;        // 지난 식사를 늦게 적었거나 시각을 과거로 고친 경우. 이미 지난 알림이라 안 만듦 (스케줄러가 바로 보내 버림)
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        return reminderRepository.save(Reminder.scheduled(user, mealId, slot, scheduledAt, now));
    }

    // 식사 시각·끼니가 바뀌었을 때: 아직 안 보낸 알림을 취소하고 새로 예약함
    // 이미 보냈거나 열어 봤거나 건너뛰었거나 혈당까지 저장한 알림이 있으면 건드리지 않음 (알림이 두 번 가는 걸 막으려고)
    @Transactional
    public Reminder rescheduleAfterMeal(Long userId, Long mealId, MealSlot mealSlot, Instant startedAt) {
        List<Reminder> reminders = reminderRepository.findByMealId(mealId);
        boolean alreadyHandled = reminders.stream()
                .anyMatch(r -> (r.getStatus() != ReminderStatus.SCHEDULED && r.getStatus() != ReminderStatus.CANCELED)
                        || r.getSentAt() != null);
        if (alreadyHandled) {
            return null;
        }
        reminders.stream()
                .filter(r -> r.getStatus() == ReminderStatus.SCHEDULED)
                .forEach(Reminder::cancel);
        return scheduleAfterMeal(userId, mealId, mealSlot, startedAt);
    }

    // 식사를 지웠을 때: 아직 안 보낸 알림만 취소함 (이미 보낸 기록은 그대로 남김)
    @Transactional
    public void cancelForMeal(Long mealId) {
        reminderRepository.findByMealIdAndStatus(mealId, ReminderStatus.SCHEDULED)
                .forEach(Reminder::cancel);   // 변경 감지로 트랜잭션 끝날 때 반영됨
    }

    // H-05 이번 끼니 건너뛰기: 예약·보냄·열어 봄 → DISMISSED (재알림·보내기 대상에서 빠짐)
    @Transactional
    public void dismissForMeal(Long mealId) {
        reminderRepository.dismissForMeal(mealId);
    }

    // 이 식사의 식후 혈당을 저장했을 때: 아직 안 끝난 알림(예약됨·보냄·열어 봄)을 DONE으로 바꾸고 혈당 id를 붙임
    // 이미 쟀는데 "재세요" 알림이 가는 걸 막음. reading_id는 알림 하나에만 붙을 수 있어서(유니크) 첫 번째에만 붙이고 나머지는 취소함
    @Transactional
    public void completeForMeal(Long mealId, Long readingId) {
        if (reminderRepository.existsByReadingId(readingId)) {
            return;             // 이 혈당은 이미 다른 알림을 끝낸 적 있음 (수정으로 식사를 바꾼 경우 등)
        }
        List<Reminder> pending = reminderRepository.findByMealIdAndStatusInOrderByIdAsc(
                mealId, List.of(ReminderStatus.SCHEDULED, ReminderStatus.SENT, ReminderStatus.OPENED,
                        ReminderStatus.DISMISSED));   // 「이번 끼니는 건너뛸게요」 뒤 그래도 쟀으면 DONE으로 붙임 (H-05)
        for (int i = 0; i < pending.size(); i++) {
            if (i == 0) pending.get(i).complete(readingId);
            else pending.get(i).cancel();
        }
    }

    // 식사 없는 혈당(공복, 자기 전)을 저장했을 때: 그날(한국) 그 시점 알림을 끝 처리 (completeForMeal과 같은 규칙)
    @Transactional
    public void completeForSlot(Long userId, MeasureSlot slot, Instant measuredAt, Long readingId) {
        if (slot != MeasureSlot.FASTING && slot != MeasureSlot.BEDTIME) {
            return;             // 식후는 식사(mealId)로 끝 처리한다
        }
        if (reminderRepository.existsByReadingId(readingId)) {
            return;
        }
        LocalDate day = measuredAt.atZone(ClockConfig.KST).toLocalDate();   // 그날 = 한국 날짜
        List<Reminder> pending = reminderRepository
                .findByUserIdAndSlotAndMealIdIsNullAndStatusInAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByIdAsc(
                        userId, slot, List.of(ReminderStatus.SCHEDULED, ReminderStatus.SENT, ReminderStatus.OPENED),
                        day.atStartOfDay(ClockConfig.KST).toInstant(),
                        day.plusDays(1).atStartOfDay(ClockConfig.KST).toInstant());
        for (int i = 0; i < pending.size(); i++) {
            if (i == 0) pending.get(i).complete(readingId);
            else pending.get(i).cancel();
        }
    }

    // 혈당의 식사·시점·잰 때·잰 시각을 고쳤을 때: 붙어 있던 알림이 지금 혈당과 안 맞으면 풀고, 지금 맞는 알림을 끝 처리
    // 맞는 알림 = 식사 알림이면 같은 식사, 식사 없는 알림(공복, 자기 전)이면 식사 없는 혈당 + 같은 시점 + 같은 날(한국)
    // 고친 뒤 끝 처리는 등록과 같은 규칙 (식사가 있으면 식사 알림, 없으면 시점 알림)
    @Transactional
    public void relinkAfterEdit(Long userId, Long readingId, Long mealId, MeasureSlot slot, Instant measuredAt) {
        Optional<Reminder> linked = reminderRepository.findByReadingId(readingId);
        if (linked.isPresent()) {
            if (matches(linked.get(), mealId, slot, measuredAt)) {
                return;
            }
            Reminder released = linked.get();
            released.release(clock.instant());
            // 건너뛴 식사의 알림이면 SENT·SCHEDULED로 돌리지 않고 DISMISSED로 (다시 울리지 않고 놓친 측정으로 셈, H-05)
            if (released.getMealId() != null && mealRepository.existsByIdAndMeasureSkippedAtIsNotNull(released.getMealId())) {
                released.dismiss();
            }
            reminderRepository.flush();   // reading_id가 unique라, 새 알림에 붙이기 전에 푼 것을 먼저 DB에 씀
        }
        if (mealId != null) {
            completeForMeal(mealId, readingId);
        } else if (slot != null) {
            completeForSlot(userId, slot, measuredAt, readingId);
        }
    }

    private static boolean matches(Reminder reminder, Long mealId, MeasureSlot slot, Instant measuredAt) {
        if (reminder.getMealId() != null) {
            return reminder.getMealId().equals(mealId);
        }
        return mealId == null && reminder.getSlot() == slot
                && reminder.getScheduledAt().atZone(ClockConfig.KST).toLocalDate()
                        .equals(measuredAt.atZone(ClockConfig.KST).toLocalDate());
    }

    // 알림을 눌러서 들어왔을 때 — 알림함의 같은 알림(재알림 포함)도 읽음으로 (H-15)
    @Transactional
    public ReminderOpenResponse open(Long userId, Long reminderId) {
        Reminder reminder = findMine(userId, reminderId);
        switch (reminder.getStatus()) {
            case SENT -> reminder.markOpened(clock.instant());
            case OPENED, DONE, DISMISSED -> {
                // 이미 열었거나 끝났거나 이번 끼니를 건너뛴 알림은 그대로 200
            }
            default -> throw new CustomException(ReminderErrorCode.CANNOT_OPEN);
        }
        Instant now = clock.instant();
        notificationRepository.findByUserIdAndReminderIdAndReadAtIsNull(userId, reminderId)
                .forEach(n -> n.markRead(now));
        return ReminderOpenResponse.from(reminder);
    }

    // 10분 뒤 다시. 받았거나 열어 본 알림만 미룰 수 있다
    @Transactional
    public ReminderSnoozeResponse snooze(Long userId, Long reminderId) {
        Reminder reminder = findMine(userId, reminderId);
        switch (reminder.getStatus()) {
            case SENT, OPENED -> reminder.snooze(clock.instant().plus(SNOOZE));
            default -> throw new CustomException(ReminderErrorCode.CANNOT_SNOOZE);
        }
        return ReminderSnoozeResponse.from(reminder);
    }

    // "내 것만". 남의 번호면 404
    private Reminder findMine(Long userId, Long reminderId) {
        return reminderRepository.findByIdAndUserId(reminderId, userId)
                .orElseThrow(() -> new CustomException(ReminderErrorCode.REMINDER_NOT_FOUND));
    }

    private static MeasureSlot afterSlotOf(final MealSlot mealSlot) {
        return switch (mealSlot) {
            case BREAKFAST -> MeasureSlot.BREAKFAST_AFTER;
            case LUNCH -> MeasureSlot.LUNCH_AFTER;
            case DINNER -> MeasureSlot.DINNER_AFTER;
            case SNACK -> null;
        };
    }
}
