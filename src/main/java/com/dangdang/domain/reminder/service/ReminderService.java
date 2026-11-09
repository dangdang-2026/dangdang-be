package com.dangdang.domain.reminder.service;

import com.dangdang.domain.criteria.dto.response.CriteriaVersionResponse;
import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.criteria.service.CriteriaService;
import com.dangdang.domain.meal.enums.MealSlot;
import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.domain.reminder.enums.ReminderStatus;
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

@Service
@RequiredArgsConstructor
public class ReminderService {
    private final ReminderRepository reminderRepository;
    private final CriteriaService criteriaService;
    private final UserRepository userRepository;
    private final Clock clock;

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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        Instant scheduledAt =
                startedAt.plus(Duration.ofMinutes(criteria.postMealMinutes()));
        return reminderRepository.save(Reminder.scheduled(user, mealId, slot, scheduledAt, clock.instant()));
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

    private static MeasureSlot afterSlotOf(final MealSlot mealSlot) {
        return switch (mealSlot) {
            case BREAKFAST -> MeasureSlot.BREAKFAST_AFTER;
            case LUNCH -> MeasureSlot.LUNCH_AFTER;
            case DINNER -> MeasureSlot.DINNER_AFTER;
            case SNACK -> null;
        };
    }
}
