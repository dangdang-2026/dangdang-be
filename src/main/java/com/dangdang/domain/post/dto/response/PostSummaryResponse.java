package com.dangdang.domain.post.dto.response;

import com.dangdang.domain.community.dto.response.AuthorResponse;
import com.dangdang.domain.post.entity.Post;
import com.dangdang.domain.post.enums.PostCategory;

import java.time.Instant;

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
        boolean hasPoll) {

    private static final int EXCERPT_LENGTH = 100;

    public static PostSummaryResponse of(Post post, Long myProfileId, long reactionCount, long commentCount,
                                         boolean reacted) {
        return new PostSummaryResponse(
                String.valueOf(post.getId()), post.getCategory(), post.getTitle(), excerpt(post.getContent()),
                AuthorResponse.of(post.getProfile(), post.isAnonymous(), myProfileId),
                post.getCreatedAt(), post.getViewCount(), reactionCount, commentCount, reacted, post.getPoll() != null);
    }

    private static String excerpt(String content) {
        if (content.codePointCount(0, content.length()) <= EXCERPT_LENGTH) {
            return content;
        }
        return content.substring(0, content.offsetByCodePoints(0, EXCERPT_LENGTH));
    }
}
