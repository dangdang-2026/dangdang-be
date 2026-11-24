package com.dangdang.domain.auth.dto.response;

import com.dangdang.domain.auth.service.KakaoLoginService;
import com.dangdang.domain.auth.service.KakaoLoginService.KakaoLoginResult;
import io.swagger.v3.oas.annotations.media.Schema;

// 카카오 로그인 결과. LOGGED_IN일 때만 쿠키가 온다
public record KakaoLoginResponse(
        @Schema(description = "LOGGED_IN(쿠키 옴) · NEEDS_SIGNUP(K-08) · EMAIL_ACCOUNT_EXISTS(K-07)", example = "NEEDS_SIGNUP")
        KakaoLoginResult result,
        @Schema(description = "가입(/kakao/signup)이나 연결(/me/kakao)에 한 번 쓰는 토큰, 10분. LOGGED_IN이면 null")
        String kakaoToken,
        @Schema(description = "EMAIL_ACCOUNT_EXISTS일 때 카카오 이메일 — 이메일 로그인 칸 미리 채우기용", example = "dahyun@gmail.com")
        String email,
        @Schema(description = "화면에 보일 가린 이메일 (K-07)", example = "da***@gmail.com")
        String maskedEmail) {

    public static KakaoLoginResponse from(KakaoLoginService.Result r) {
        return new KakaoLoginResponse(r.result(), r.kakaoToken(), r.email(), r.email() == null ? null : mask(r.email()));
    }

    // 앞 두 글자만 남긴다 (두 글자 이하면 한 글자). da***@gmail.com
    static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        int keep = at <= 2 ? 1 : 2;
        return email.substring(0, keep) + "***" + email.substring(at);
    }
}
