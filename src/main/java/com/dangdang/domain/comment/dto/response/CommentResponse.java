package com.dangdang.domain.comment.dto.response;

import com.dangdang.domain.comment.entity.Comment;
import com.dangdang.domain.community.dto.response.AuthorResponse;

import java.time.Instant;

public record CommentResponse(
        String id,
        String postId,
        String parentId,
        String content,
        AuthorResponse author,
        Instant createdAt,
        String visibility,
        String hiddenReason,
        boolean isMine,
        long reactionCount,
        boolean reacted) {

    public static CommentResponse of(Comment comment, Long myProfileId, long reactionCount, boolean reacted) {
        AuthorResponse author = AuthorResponse.of(comment.getProfile(), comment.isAnonymous(), myProfileId);
        String parentId = comment.getParent() == null ? null : String.valueOf(comment.getParent().getId());
        return new CommentResponse(
                String.valueOf(comment.getId()), String.valueOf(comment.getPost().getId()), parentId,
                comment.getContent(), author, comment.getCreatedAt(), "VISIBLE", null,
                author.isMine(), reactionCount, reacted);
    }
}
