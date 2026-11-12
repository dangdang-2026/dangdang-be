package com.dangdang.domain.post.entity;

import com.dangdang.domain.community.entity.CommunityProfile;
import com.dangdang.domain.post.enums.PostCategory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Getter
@Entity
@Table(name = "posts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id")
    private CommunityProfile profile;        // 탈퇴하면 null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostCategory category;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_anonymous", nullable = false)
    private boolean anonymous;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> poll;               // 투표 항목. 없으면 null

    @Column(name = "is_notice", nullable = false)
    private boolean notice;

    @Column(nullable = false)
    private int viewCount;

    @Column(length = 50)
    private String hiddenReason;             // 운영자가 가리면 채워진다 (6주차)

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant deletedAt;               // 지우면 시각만 적는다 (soft delete)

    public static Post of(CommunityProfile profile, PostCategory category, String title, String content,
                          boolean anonymous, List<String> poll, Instant now) {
        Post post = new Post();
        post.profile = profile;
        post.category = category;
        post.title = title;
        post.content = content;
        post.anonymous = anonymous;
        post.poll = poll;
        post.createdAt = now;
        post.updatedAt = now;
        return post;
    }

    public void update(PostCategory category, String title, String content, Instant now) {
        if (category != null) {
            this.category = category;
        }
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        this.updatedAt = now;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
    }

    public boolean isOwnedBy(Long profileId) {
        return profile != null && profile.getId().equals(profileId);
    }

    public boolean isHidden() {
        return hiddenReason != null;
    }
}
