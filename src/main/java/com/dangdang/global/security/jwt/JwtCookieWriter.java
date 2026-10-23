package com.dangdang.global.security.jwt;

import com.dangdang.global.config.property.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

// 로그인·재발급·로그아웃 응답에 붙일 쿠키를 만든다
@Component
@RequiredArgsConstructor
public class JwtCookieWriter {

    private final JwtProperties jwtProperties;

    public ResponseCookie accessCookie(String token) {
        return cookie(TokenType.ACCESS_TOKEN, token, jwtProperties.accessTokenValidityInSeconds());
    }

    public ResponseCookie refreshCookie(String token) {
        return cookie(TokenType.REFRESH_TOKEN, token, jwtProperties.refreshTokenValidityInSeconds());
    }

    // 로그아웃: 같은 이름의 빈 쿠키를 0초짜리로 보내면 브라우저가 지운다
    public ResponseCookie expire(TokenType type) {
        return cookie(type, "", 0);
    }

    private ResponseCookie cookie(TokenType type, String value, long maxAgeSeconds) {
        return ResponseCookie.from(type.name(), value)
                .httpOnly(true) // 자바스크립트가 못 읽게 (XSS 로 토큰 훔치기 방지)
                .secure(jwtProperties.secure()) // 운영: https 에서만
                .sameSite(jwtProperties.sameSite()) // 다른 사이트에서 몰래 보내는 요청에 안 실리게
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }
}
