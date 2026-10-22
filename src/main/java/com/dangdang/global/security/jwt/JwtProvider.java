package com.dangdang.global.security.jwt;

import com.dangdang.domain.user.enums.Role;
import com.dangdang.global.config.property.JwtProperties;
import com.dangdang.global.security.AuthPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtProvider {

    private static final String TYPE = "type";
    private static final String ROLE = "role";
    private static final String BEARER = "Bearer";

    private final JwtProperties jwtProperties;

    private SecretKey key(){
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(AuthPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(principal.userId()))
                .claim(TYPE, TokenType.ACCESS_TOKEN.name())
                .claim(ROLE, principal.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(jwtProperties.accessTokenValidityInSeconds())))
                .signWith(key())
                .compact();
    }

    // jti = 이 Refresh 의 고유 번호. Redis 에 이 번호로 저장해 두고, 쓰면 지운다
    public String createRefreshToken(Long userId, String jti) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TYPE, TokenType.REFRESH_TOKEN.name())
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(jwtProperties.refreshTokenValidityInSeconds())))
                .signWith(key())
                .compact();
    }

    // 서명·만료·종류를 검사하고 내용을 돌려준다. 문제가 있으면 JwtException 을 던진다
    public Claims parse(String token, TokenType expected) {
        Claims claims = Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
        if (!expected.name().equals(claims.get(TYPE, String.class))) {
            throw new MalformedJwtException("토큰 종류가 다름: " + expected);
        }
        return claims;
    }

    public AuthPrincipal toPrincipal(Claims claims) {
        return new AuthPrincipal(
                Long.valueOf(claims.getSubject()), Role.valueOf(claims.get(ROLE, String.class)));
    }

    // Access: Authorization 헤더(Bearer) 먼저, 없으면 쿠키
    public String resolveAccessToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            return header.substring(BEARER.length());
        }
        return cookieValue(request, TokenType.ACCESS_TOKEN);
    }

    // Refresh: 쿠키에서만
    public String resolveRefreshToken(HttpServletRequest request) {
        return cookieValue(request, TokenType.REFRESH_TOKEN);
    }

    private String cookieValue(HttpServletRequest request, TokenType type) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (type.name().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
