package com.dangdang.domain.comment.dto.response;

import com.dangdang.domain.comment.entity.Comment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

// 마이 › 내가 쓴 댓글 한 줄 (M-25). 「원글 · 제목」을 누르면 postId로 글 상세
public record MyCommentResponse(
        String id,
        String postId,
        @Schema(description = "댓글을 단 글 제목 (「원글 · 제목」)") String postTitle,
        @Schema(description = "답글이면 부모 댓글 id, 아니면 null") String parentId,
        String content,
        boolean anonymous,
        @Schema(description = "이 댓글에 달린 공감 수") long reactionCount,
        Instant createdAt) {

    public static MyCommentResponse of(Comment comment, long reactionCount) {
        return new MyCommentResponse(
                String.valueOf(comment.getId()),
                String.valueOf(comment.getPost().getId()),
                comment.getPost().getTitle(),
                comment.getParent() == null ? null : String.valueOf(comment.getParent().getId()),
                comment.getContent(),
                comment.isAnonymous(),
                reactionCount,
                comment.getCreatedAt());
    }
}
