package com.dangdang.domain.post.dto.response;

import com.dangdang.domain.comment.entity.Comment;
import com.dangdang.domain.community.dto.response.AuthorResponse;
import io.swagger.v3.oas.annotations.media.Schema;

// U-23 인기 카드 아래 베스트 댓글 한 줄. 익명 댓글이면 「익명 산모」 (번호는 글 안에서만 뜻이 있어 안 붙인다)
public record BestCommentResponse(
        String id,
        @Schema(description = "댓글 앞부분 60자", example = "저도 아침 공복이 제일 어려웠어요") String excerpt,
        @Schema(description = "글쓴이. 이 한 줄에는 주수 배지를 안 붙인다 (weekBadge 늘 null)") AuthorResponse author,
        long reactionCount) {

    private static final int EXCERPT_LENGTH = 60;

    public static BestCommentResponse of(Comment comment, long reactionCount, Long myProfileId) {
        String text = comment.getContent();
        String excerpt = text.codePointCount(0, text.length()) <= EXCERPT_LENGTH
                ? text : text.substring(0, text.offsetByCodePoints(0, EXCERPT_LENGTH));   // 이모지를 반으로 안 자르게
        return new BestCommentResponse(String.valueOf(comment.getId()), excerpt,
                AuthorResponse.of(comment.getProfile(), comment.isAnonymous(), myProfileId), reactionCount);
    }
}
