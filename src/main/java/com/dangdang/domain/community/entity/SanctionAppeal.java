package com.dangdang.domain.community.entity;

import com.dangdang.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// 이용 제한 이의 신청 (U-17). 제한 하나에 한 번만 (uk_sanction_appeals)
@Getter
@Entity
@Table(name = "sanction_appeals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SanctionAppeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sanction_id")
    private UserSanction sanction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static SanctionAppeal of(UserSanction sanction, User user, String content, Instant now) {
        SanctionAppeal a = new SanctionAppeal();
        a.sanction = sanction;
        a.user = user;
        a.content = content;
        a.createdAt = now;
        return a;
    }
}
