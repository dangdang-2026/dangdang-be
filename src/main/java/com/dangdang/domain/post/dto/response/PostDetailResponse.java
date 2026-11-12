package com.dangdang.domain.post.dto.response;

import com.dangdang.domain.community.dto.response.AuthorResponse;
import com.dangdang.domain.post.entity.Post;
import com.dangdang.domain.post.enums.PostCategory;

import java.time.Instant;

public record PostDetailResponse(
        String id,
        PostCategory category,
        String title,
        String content,
        AuthorResponse author,
        Instant createdAt,
        Instant updatedAt,
        int viewCount,
        long reactionCount,
        long commentCount,
        boolean reacted,
        boolean isMine,
        String visibility,
        String hiddenReason,
        PollResponse poll) {

    public static PostDetailResponse of(Post post, Long myProfileId, int viewCount, long reactionCount,
                                        long commentCount, boolean reacted, PollResponse poll) {
        AuthorResponse author = AuthorResponse.of(post.getProfile(), post.isAnonymous(), myProfileId);
        return new PostDetailResponse(
                String.valueOf(post.getId()), post.getCategory(), post.getTitle(), post.getContent(), author,
                post.getCreatedAt(), post.getUpdatedAt(), viewCount, reactionCount, commentCount, reacted,
                author.isMine(), "VISIBLE", null, poll);
    }

    // U-14: 가려진 글은 게시판과 이유만 내려가고 제목·본문·글쓴이는 비운다
    public static PostDetailResponse hidden(Post post) {
        return new PostDetailResponse(
                String.valueOf(post.getId()), post.getCategory(), null, null, null,
                post.getCreatedAt(), post.getUpdatedAt(), post.getViewCount(), 0, 0, false, false,
                "HIDDEN", post.getHiddenReason(), null);
    }
}
