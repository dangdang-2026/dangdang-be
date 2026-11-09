package com.dangdang.domain.user.repository;

import com.dangdang.domain.user.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, String> {

    Optional<NotificationPreference> findByUserId(Long userId);
}
