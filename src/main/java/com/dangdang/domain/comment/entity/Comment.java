package com.dangdang.domain.comment.entity;

import com.dangdang.domain.community.entity.CommunityProfile;
import com.dangdang.domain.post.entity.Post;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Getter
@Entity
@DynamicUpdate                     // 바뀐 칸만 UPDATE (Post와 같은 이유)
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

    @Column(name = "anonymous_no")
    private Integer anonymousNo;             // 같은 글 안 「익명 1·2」 번호. 이름 댓글·익명 글의 글쓴이 댓글은 null

    @Column(length = 50)
    private String hiddenReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant deletedAt;

    public static Comment of(Post post, CommunityProfile profile, Comment parent, String content,
                             boolean anonymous, Integer anonymousNo, Instant now) {
        Comment comment = new Comment();
        comment.post = post;
        comment.profile = profile;
        comment.parent = parent;
        comment.content = content;
        comment.anonymous = anonymous;
        comment.anonymousNo = anonymousNo;
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

    // U-02b 「글쓴이」 라벨. 글과 댓글의 익명 여부가 같을 때만 붙인다 (이름 글은 이름 댓글만, 익명 글은 익명 댓글만)
    // 이름 글에 익명으로 단 댓글에 붙이면 그 익명 댓글이, 익명 글에 이름으로 단 댓글에 붙이면 익명 글쓴이가 누군지 드러난다
    public boolean isByPostAuthor() {
        return profile != null && post.isOwnedBy(profile.getId()) && anonymous == post.isAnonymous();
    }
}
