package com.dangdang.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.time.LocalTime;

@Getter
@Entity
@Table(name = "notification_preferences")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(nullable = false)
    private boolean measurementEnabled = true;

    @Column(nullable = false)
    private boolean renotifyEnabled = true;

    @Column(nullable = false)
    private boolean hidePreview = true;

    @Column(nullable = false)
    private LocalTime fastingAlarmTime = LocalTime.of(7, 0);

    @Column(nullable = false)
    private LocalTime bedtimeAlarmTime = LocalTime.of(22, 0);

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    // 가입할 때 기본값으로 한 줄 만든다
    public static NotificationPreference defaultsFor(User user) {
        NotificationPreference pref = new NotificationPreference();
        pref.user = user;
        return pref;
    }
}
