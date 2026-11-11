package com.dangdang.domain.reminder.service;

import com.dangdang.domain.criteria.dto.response.CriteriaVersionResponse;
import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.criteria.service.CriteriaService;
import com.dangdang.domain.meal.enums.MealSlot;
import com.dangdang.domain.reminder.dto.response.ReminderOpenResponse;
import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.domain.reminder.enums.ReminderStatus;
import com.dangdang.domain.reminder.exception.ReminderErrorCode;
import com.dangdang.domain.reminder.repository.ReminderRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReminderService {
    private final ReminderRepository reminderRepository;
    private final CriteriaService criteriaService;
    private final UserRepository userRepository;
    private final Clock clock;
    private final Optional<LocalDemoDelay> demoDelay;

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
    // 이미 보냈거나 열어 봤거나 혈당까지 저장한 알림이 있으면 건드리지 않음 (알림이 두 번 가는 걸 막으려고)
    @Transactional
    public Reminder rescheduleAfterMeal(Long userId, Long mealId, MealSlot mealSlot, Instant startedAt) {
        List<Reminder> reminders = reminderRepository.findByMealId(mealId);
        boolean alreadyHandled = reminders.stream()
                .anyMatch(r -> r.getStatus() != ReminderStatus.SCHEDULED && r.getStatus() != ReminderStatus.CANCELED);
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

    // 이 식사의 식후 혈당을 저장했을 때: 아직 안 끝난 알림(예약됨·보냄·열어 봄)을 DONE으로 바꾸고 혈당 id를 붙임
    // 이미 쟀는데 "재세요" 알림이 가는 걸 막음. reading_id는 알림 하나에만 붙을 수 있어서(유니크) 첫 번째에만 붙이고 나머지는 취소함
    @Transactional
    public void completeForMeal(Long mealId, Long readingId) {
        if (reminderRepository.existsByReadingId(readingId)) {
            return;             // 이 혈당은 이미 다른 알림을 끝낸 적 있음 (수정으로 식사를 바꾼 경우 등)
        }
        List<Reminder> pending = reminderRepository.findByMealIdAndStatusInOrderByIdAsc(
                mealId, List.of(ReminderStatus.SCHEDULED, ReminderStatus.SENT, ReminderStatus.OPENED));
        for (int i = 0; i < pending.size(); i++) {
            if (i == 0) pending.get(i).complete(readingId);
            else pending.get(i).cancel();
        }
    }

    // 알림을 눌러서 들어왔을 때
    @Transactional
    public ReminderOpenResponse open(Long userId, Long reminderId) {
        Reminder reminder = findMine(userId, reminderId);
        switch (reminder.getStatus()) {
            case SENT -> reminder.markOpened(clock.instant());
            case OPENED, DONE -> {
                // 이미 열었거나 끝난 알림은 그대로 200
            }
            default -> throw new CustomException(ReminderErrorCode.CANNOT_OPEN);
        }
        return ReminderOpenResponse.from(reminder);
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
