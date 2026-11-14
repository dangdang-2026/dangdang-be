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
@Table(name = "post_drafts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id")
    private CommunityProfile profile;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PostCategory category;

    @Column(length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_anonymous", nullable = false)
    private boolean anonymous;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> poll;

    @Column(nullable = false)
    private Instant updatedAt;

    public static PostDraft empty(CommunityProfile profile) {
        PostDraft draft = new PostDraft();
        draft.profile = profile;
        return draft;
    }

    // PUT이라 통째로 바꾼다
    public void overwrite(PostCategory category, String title, String content, boolean anonymous,
                          List<String> poll, Instant now) {
        this.category = category;
        this.title = title;
        this.content = content;
        this.anonymous = anonymous;
        this.poll = poll;
        this.updatedAt = now;
    }
}
