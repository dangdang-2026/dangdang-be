package com.dangdang.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record WithdrawalRequest(
        @Schema(description = "지금 비밀번호 (이메일 가입자만, 카카오는 안 보냄)", example = "abcd1234") String password,
        @Schema(description = "커뮤니티 글을 남길지. false(기본) = 내 글·댓글 모두 지움, true = 남기고 작성자를 「탈퇴한 회원」으로",
                example = "false") Boolean keepPosts,
        @Schema(description = "카카오 가입자만: 탈퇴 직전에 카카오로 다시 로그인해 받은 인가 코드 (M-08)") String kakaoCode,
        @Schema(description = "카카오 가입자만: 그 코드를 받은 주소 (로그인과 같은 /auth/kakao/callback)",
                example = "https://dang-dang.site/auth/kakao/callback") String redirectUri) {
}
