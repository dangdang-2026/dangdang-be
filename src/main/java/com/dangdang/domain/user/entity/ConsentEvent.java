package com.dangdang.domain.user.entity;

import com.dangdang.domain.user.enums.ConsentType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name="consent_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch=FetchType.LAZY, optional = false)
    @JoinColumn(name="user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConsentType consentType;

    @Column(nullable = false, length = 40)
    private String documentVersion;

    @Column(nullable = false)
    private boolean granted;

    @Column(nullable = false)
    private Instant occurredAt;

    public static ConsentEvent of(
            User user, ConsentType type, String documentVersion, boolean granted) {
        ConsentEvent event = new ConsentEvent();
        event.user = user;
        event.consentType = type;
        event.documentVersion = documentVersion;
        event.granted = granted;
        event.occurredAt = Instant.now();
        return event;
    }
}
