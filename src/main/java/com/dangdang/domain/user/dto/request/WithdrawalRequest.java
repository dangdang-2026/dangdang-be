package com.dangdang.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record WithdrawalRequest(
        @Schema(description = "지금 비밀번호 (이메일 가입자만, 카카오는 안 보냄)", example = "abcd1234") String password,
        @Schema(description = "커뮤니티 글을 남길지. false(기본) = 내 글·댓글 모두 지움, true = 남기고 작성자를 「탈퇴한 회원」으로",
                example = "false") Boolean keepPosts) {
}
