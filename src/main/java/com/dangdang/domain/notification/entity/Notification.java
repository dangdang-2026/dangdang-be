package com.dangdang.domain.notification.entity;

import com.dangdang.domain.notification.enums.NotificationType;
import com.dangdang.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private Long reminderId;                 // 측정 알림일 때만

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 200)
    private String body;

    @Column(nullable = false, length = 200)
    private String deepLink;                 // 누르면 갈 화면

    @Column(nullable = false)
    private Instant sentAt;

    private Instant readAt;

    public static Notification measure(User user, Long reminderId, String title, String body,
                                       String deepLink, Instant sentAt) {
        Notification n = new Notification();
        n.user = user;
        n.reminderId = reminderId;
        n.type = NotificationType.MEASURE;
        n.title = title;
        n.body = body;
        n.deepLink = deepLink;
        n.sentAt = sentAt;
        return n;
    }

    // 산후 검사 전날·당일 알림 (내용 칸은 비움)
    public static Notification postpartum(User user, String title, String deepLink, Instant sentAt) {
        Notification n = new Notification();
        n.user = user;
        n.type = NotificationType.POSTPARTUM;
        n.title = title;
        n.deepLink = deepLink;
        n.sentAt = sentAt;
        return n;
    }

    // 광고성 정보 수신 동의·거부 결과 (N-09b, 정보통신망법 제50조 제8항: 전송자·날짜·처리 결과)
    public static Notification marketing(User user, String title, String body, String deepLink, Instant sentAt) {
        Notification n = new Notification();
        n.user = user;
        n.type = NotificationType.MARKETING;
        n.title = title;
        n.body = body;
        n.deepLink = deepLink;
        n.sentAt = sentAt;
        return n;
    }

    // 처음 읽은 시각만 남긴다
    public void markRead(Instant now) {
        if (this.readAt == null) {
            this.readAt = now;
        }
    }
}
