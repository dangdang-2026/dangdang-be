package com.dangdang.domain.community.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "profile_blocks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id")
    private CommunityProfile profile;          // 안 보기 한 사람

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_profile_id")
    private CommunityProfile blockedProfile;   // 안 보이는 사람

    @Column(name = "via_anonymous", nullable = false)
    private boolean viaAnonymous;              // 익명 글·댓글에서 막았으면 true → 목록에 「익명 사용자」

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
