package com.dangdang.domain.user.repository;

import com.dangdang.domain.user.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, String> {

    Optional<NotificationPreference> findByUserId(Long userId);

    // 공복과 자기 전 알림을 만들 사람 — 측정 알림을 켠 사람만, 회원도 같이 읽는다 (사람마다 다시 안 부르게)
    @Query("select p from NotificationPreference p join fetch p.user where p.measurementEnabled = true")
    List<NotificationPreference> findAllMeasurementEnabled();
}
