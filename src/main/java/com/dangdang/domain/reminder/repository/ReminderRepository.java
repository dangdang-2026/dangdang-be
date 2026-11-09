package com.dangdang.domain.reminder.repository;

import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.domain.reminder.enums.ReminderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    // 이 식사로 생긴 알림 전부 (상태 상관없이)
    List<Reminder> findByMealId(Long mealId);

    // 이 식사로 생긴 알림 중 특정 상태인 것
    List<Reminder> findByMealIdAndStatus(Long mealId, ReminderStatus status);
}
