package com.dangdang.domain.comment.entity;

import com.dangdang.domain.community.entity.CommunityProfile;
import com.dangdang.domain.post.entity.Post;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id")
    private CommunityProfile profile;        // 탈퇴하면 null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;                  // 답글이면 부모 댓글

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_anonymous", nullable = false)
    private boolean anonymous;

    @Column(length = 50)
    private String hiddenReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant deletedAt;

    public static Comment of(Post post, CommunityProfile profile, Comment parent, String content,
                             boolean anonymous, Instant now) {
        Comment comment = new Comment();
        comment.post = post;
        comment.profile = profile;
        comment.parent = parent;
        comment.content = content;
        comment.anonymous = anonymous;
        comment.createdAt = now;
        comment.updatedAt = now;
        return comment;
    }

    public void edit(String content, Instant now) {
        this.content = content;
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

    public boolean isReply() {
        return parent != null;
    }
}
