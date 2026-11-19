package com.dangdang.domain.community.entity;

import com.dangdang.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "user_sanctions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSanction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 50)
    private String reason;

    @Column(nullable = false)
    private Instant startsAt;

    private Instant endsAt;          // null = 운영자가 풀 때까지

    private Instant liftedAt;

    private Long createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static UserSanction of(User user, String reason, Instant startsAt, Instant endsAt, Long adminId) {
        UserSanction s = new UserSanction();
        s.user = user;
        s.reason = reason;
        s.startsAt = startsAt;
        s.endsAt = endsAt;
        s.createdBy = adminId;
        s.createdAt = startsAt;
        return s;
    }

    public boolean isActiveAt(Instant now) {
        return liftedAt == null && !now.isBefore(startsAt) && (endsAt == null || now.isBefore(endsAt));
    }

    public void lift(Instant now) {
        if (liftedAt == null) {
            liftedAt = now;
        }
    }
}
