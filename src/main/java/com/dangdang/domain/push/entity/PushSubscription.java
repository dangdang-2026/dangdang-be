package com.dangdang.domain.push.entity;

import com.dangdang.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "push_subscriptions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String endpoint;                 // 브라우저가 준 알림 주소

    @Column(nullable = false)
    private String p256dh;                   // 브라우저 공개키 — 내용을 암호화할 때 씀

    @Column(nullable = false)
    private String authSecret;               // 브라우저 비밀값 — 암호화할 때 같이 씀

    private String userAgent;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant revokedAt;               // null이면 살아 있는 구독

    public static PushSubscription of(User user, String endpoint, String p256dh, String authSecret,
                                      String userAgent, Instant now) {
        PushSubscription s = new PushSubscription();
        s.user = user;
        s.endpoint = endpoint;
        s.p256dh = p256dh;
        s.authSecret = authSecret;
        s.userAgent = userAgent;
        s.createdAt = now;
        return s;
    }

    // 같은 기기가 다시 구독할 때 — 지금 로그인한 사람으로 바꾸고 다시 살린다
    public void renew(User user, String p256dh, String authSecret, String userAgent) {
        this.user = user;
        this.p256dh = p256dh;
        this.authSecret = authSecret;
        this.userAgent = userAgent;
        this.revokedAt = null;
    }

    // 끊기 — 이미 끊긴 줄이면 처음 끊은 시각을 그대로 둔다
    public void revoke(Instant now) {
        if (this.revokedAt == null) {
            this.revokedAt = now;
        }
    }
}
