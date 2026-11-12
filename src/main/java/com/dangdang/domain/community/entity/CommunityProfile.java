package com.dangdang.domain.community.entity;

import com.dangdang.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "community_profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(nullable = false)
    private boolean showWeekBadge;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static CommunityProfile of(User user, String nickname, boolean showWeekBadge, Instant now) {
        CommunityProfile profile = new CommunityProfile();
        profile.user = user;
        profile.nickname = nickname;
        profile.showWeekBadge = showWeekBadge;
        profile.createdAt = now;
        profile.updatedAt = now;
        return profile;
    }

    public void update(String nickname, Boolean showWeekBadge, Instant now) {
        if (nickname != null) {
            this.nickname = nickname;
        }
        if (showWeekBadge != null) {
            this.showWeekBadge = showWeekBadge;
        }
        this.updatedAt = now;
    }
}
