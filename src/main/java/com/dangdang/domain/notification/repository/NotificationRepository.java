package com.dangdang.domain.notification.repository;

import com.dangdang.domain.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Slice<Notification> findByUserId(Long userId, Pageable pageable);

    // "내 것만" — 남의 번호면 비어서 나온다
    Optional<Notification> findByIdAndUserId(Long id, Long userId);
}
