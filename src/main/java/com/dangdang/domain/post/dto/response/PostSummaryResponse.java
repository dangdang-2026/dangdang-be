package com.dangdang.domain.post.dto.response;

import com.dangdang.domain.community.dto.response.AuthorResponse;
import com.dangdang.domain.post.entity.Post;
import com.dangdang.domain.post.enums.PostCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

public record PostSummaryResponse(
        String id,
        PostCategory category,
        String title,
        String excerpt,
        AuthorResponse author,
        Instant createdAt,
        int viewCount,
        long reactionCount,
        long commentCount,
        boolean reacted,
        boolean hasPoll,
        @Schema(description = "인기 목록(sort=POPULAR)에서만: 베스트 댓글 (U-23). 공감 3개 이상인 댓글이 없거나 다른 목록이면 null")
        BestCommentResponse bestComment) {

    private static final int EXCERPT_LENGTH = 100;

    // badges: profileId → 주수 배지 (WeekBadges)
    public static PostSummaryResponse of(Post post, Long myProfileId, long reactionCount, long commentCount,
                                         boolean reacted, Map<Long, String> badges) {
        return new PostSummaryResponse(
                String.valueOf(post.getId()), post.getCategory(), post.getTitle(), excerpt(post.getContent()),
                AuthorResponse.of(post.getProfile(), post.isAnonymous(), myProfileId, badges),
                post.getCreatedAt(), post.getViewCount(), reactionCount, commentCount, reacted, post.getPoll() != null,
                null);
    }

    public PostSummaryResponse withBestComment(BestCommentResponse best) {
        return new PostSummaryResponse(id, category, title, excerpt, author, createdAt, viewCount, reactionCount,
                commentCount, reacted, hasPoll, best);
    }

    private static String excerpt(String content) {
        if (content.codePointCount(0, content.length()) <= EXCERPT_LENGTH) {
            return content;
        }
        return content.substring(0, content.offsetByCodePoints(0, EXCERPT_LENGTH));
    }
}
