package com.dangdang.domain.notification.repository;

import com.dangdang.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
