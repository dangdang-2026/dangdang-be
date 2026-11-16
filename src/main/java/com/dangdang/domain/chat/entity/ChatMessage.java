package com.dangdang.domain.chat.entity;

import com.dangdang.domain.chat.enums.ChatKind;
import com.dangdang.domain.chat.enums.ChatRole;
import com.dangdang.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChatRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatKind kind;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)             // DB 칸이 JSONB라서
    private ChatPayload payload;

    private Boolean helpful;                 // 도움이 됐나요 (ASK-08)

    @Column(length = 40)
    private String requestId;

    @Column(length = 30)
    private String promptVersion;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static ChatMessage question(User user, String content, Instant now) {
        return of(user, ChatRole.USER, ChatKind.QUESTION, content, null, null, null, now);
    }

    public static ChatMessage answer(User user, ChatKind kind, String content, ChatPayload payload,
                                     String requestId, String promptVersion, Instant now) {
        return of(user, ChatRole.ASSISTANT, kind, content, payload, requestId, promptVersion, now);
    }

    private static ChatMessage of(User user, ChatRole role, ChatKind kind, String content, ChatPayload payload,
                                  String requestId, String promptVersion, Instant now) {
        ChatMessage m = new ChatMessage();
        m.user = user;
        m.role = role;
        m.kind = kind;
        m.content = content;
        m.payload = payload;
        m.requestId = requestId;
        m.promptVersion = promptVersion;
        m.createdAt = now;
        return m;
    }

    public void rate(boolean helpful) {
        this.helpful = helpful;
    }
}
