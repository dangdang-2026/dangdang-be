package com.dangdang.domain.reminder.repository;

import com.dangdang.domain.reminder.entity.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {
}
