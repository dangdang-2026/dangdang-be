package com.dangdang.domain.comment.dto.response;

import com.dangdang.domain.comment.entity.Comment;
import com.dangdang.domain.community.dto.response.AuthorResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

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
        boolean reacted,
        @Schema(description = "글쓴이가 단 댓글 (U-02b 「글쓴이」 라벨). 글과 댓글의 익명 여부가 같을 때만 true (이름 글은 이름 댓글, 익명 글은 익명 댓글)")
        boolean isPostAuthor,
        @Schema(description = "같은 글 안 익명 번호 (「익명 1」). 이름 댓글·「글쓴이」 댓글·탈퇴한 사람이면 null. "
                + "같은 사람은 이 글에서 늘 같은 번호, 지워도 다른 사람 번호가 밀리지 않는다", example = "1")
        Integer anonymousNo,
        @Schema(description = "베스트 댓글 (U-02b 「Best」) — 답글이 아닌 댓글 중 공감 3개 이상 가장 많은 것 하나, "
                + "같으면 먼저 단 것. 댓글 목록에서만 계산하고 쓰기·고치기 응답은 false")
        boolean isBest) {

    // badges: profileId → 주수 배지 (WeekBadges). 익명 댓글엔 안 붙는다
    public static CommentResponse of(Comment comment, Long myProfileId, long reactionCount, boolean reacted,
                                     boolean best, Map<Long, String> badges) {
        AuthorResponse author = AuthorResponse.of(comment.getProfile(), comment.isAnonymous(), myProfileId, badges);
        String parentId = comment.getParent() == null ? null : String.valueOf(comment.getParent().getId());
        Integer anonymousNo = comment.getProfile() == null ? null : comment.getAnonymousNo();
        return new CommentResponse(
                String.valueOf(comment.getId()), String.valueOf(comment.getPost().getId()), parentId,
                comment.getContent(), author, comment.getCreatedAt(), "VISIBLE", null,
                author.isMine(), reactionCount, reacted,
                comment.isByPostAuthor(), anonymousNo, best);
    }
}
