package com.dangdang.domain.user.repository;

import com.dangdang.domain.user.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, String> {
}
