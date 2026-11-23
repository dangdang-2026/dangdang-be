package com.dangdang.domain.post.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

// 마이 › 커뮤니티 설정 (M-14) 「내가 쓴 글 4개 · 내가 쓴 댓글 12개 · 공감한 글 9개 · 안 보는 사람 2명」
public record MyCommunityCountsResponse(
        @Schema(description = "내가 쓴 글 (GET /api/me/posts와 같은 조건)") long postCount,
        @Schema(description = "내가 쓴 댓글 (GET /api/me/comments와 같은 조건)") long commentCount,
        @Schema(description = "공감한 글 (GET /api/me/reactions와 같은 조건)") long reactedPostCount,
        @Schema(description = "안 보는 사람 (GET /api/blocks 전체 수)") long blockedCount) {

    public static MyCommunityCountsResponse empty() {
        return new MyCommunityCountsResponse(0, 0, 0, 0);
    }
}
