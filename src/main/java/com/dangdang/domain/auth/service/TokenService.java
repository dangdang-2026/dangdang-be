package com.dangdang.domain.auth.service;

import com.dangdang.domain.auth.dto.response.TokenResponse;
import com.dangdang.domain.auth.exception.AuthErrorCode;
import com.dangdang.domain.auth.infra.RefreshTokenStore;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.config.property.JwtProperties;
import com.dangdang.global.exception.CustomException;
import com.dangdang.global.security.AuthPrincipal;
import com.dangdang.global.security.jwt.JwtProvider;
import com.dangdang.global.security.jwt.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenStore refreshTokenStore;
    private final UserRepository userRepository;

    // Access + Refresh 를 만들고, Refresh 는 Redis 에 적어 둔다
    public TokenResponse issue(AuthPrincipal principal) {
        String jti = UUID.randomUUID().toString();
        String accessToken = jwtProvider.createAccessToken(principal);
        String refreshToken = jwtProvider.createRefreshToken(principal.userId(), jti);
        refreshTokenStore.save(
                jti, principal.userId(), Duration.ofSeconds(jwtProperties.refreshTokenValidityInSeconds()));
        return new TokenResponse(accessToken, refreshToken);
    }

    // 재발급: 쓴 Refresh 는 지우고 새로 한 쌍 (한 번 쓰면 끝 = 훔친 토큰을 오래 못 씀)
    @Transactional(readOnly = true)
    public TokenResponse reissue(String refreshToken) {
        Claims claims = parseRefresh(refreshToken);
        if (!refreshTokenStore.delete(claims.getId())) {
            log.warn("[Auth] 이미 쓴(또는 로그아웃한) Refresh - 사용자: {}", claims.getSubject());
            throw new CustomException(AuthErrorCode.INVALID_TOKEN);
        }
        User user = userRepository.findById(Long.valueOf(claims.getSubject()))
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN)); // 탈퇴한 회원
        log.info("[Auth] 토큰 재발급 - 사용자: {}", user.getId());
        return issue(AuthPrincipal.from(user));
    }

    // 로그아웃: Redis 에서 지우기만. 이상한 토큰이어도 에러 없이 끝낸다 (쿠키는 어차피 지움)
    public void revoke(String refreshToken) {
        try {
            Claims claims = jwtProvider.parse(refreshToken, TokenType.REFRESH_TOKEN);
            refreshTokenStore.delete(claims.getId());
            log.info("[Auth] 로그아웃 - 사용자: {}", claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            log.info("[Auth] 로그아웃 - 토큰 없음 또는 만료");
        }
    }

    private Claims parseRefresh(String refreshToken) {
        if (refreshToken == null) {
            throw new CustomException(AuthErrorCode.INVALID_TOKEN);
        }
        try {
            return jwtProvider.parse(refreshToken, TokenType.REFRESH_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(AuthErrorCode.INVALID_TOKEN);
        }
    }
}
