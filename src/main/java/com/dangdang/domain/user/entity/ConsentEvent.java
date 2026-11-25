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

    // 동의·철회 한 줄. 시각은 앱 Clock에서 받는다 (테스트에서 시계를 돌릴 수 있게)
    public static ConsentEvent of(
            User user, ConsentType type, String documentVersion, boolean granted, Instant occurredAt) {
        ConsentEvent event = new ConsentEvent();
        event.user = user;
        event.consentType = type;
        event.documentVersion = documentVersion;
        event.granted = granted;
        event.occurredAt = occurredAt;
        return event;
    }

    // 동의한 상태이고, 그때 문서가 지금 버전인지 — 「동의했다」를 판단하는 규칙은 여기 하나 (78 E)
    // 문서 버전을 올리면 예전 동의는 false → 필수는 A-01로, 선택은 꺼진 것으로 보인다
    public boolean isCurrentGrant() {
        return granted && consentType.getVersion().equals(documentVersion);
    }
}
