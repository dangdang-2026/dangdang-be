package com.dangdang.global.filter;

import com.dangdang.domain.auth.exception.AuthErrorCode;
import com.dangdang.global.security.AuthErrorWriter;
import com.dangdang.global.security.AuthPrincipal;
import com.dangdang.global.security.jwt.JwtProvider;
import com.dangdang.global.security.jwt.TokenType;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final AuthErrorWriter authErrorWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/auth/")
                || uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = jwtProvider.resolveAccessToken(request);
        if (token == null) { // 토큰 없음 → 그냥 통과 (막을지는 SecurityConfig 가 정한다)
            filterChain.doFilter(request, response);
            return;
        }
        try {
            AuthPrincipal principal = jwtProvider.toPrincipal(jwtProvider.parse(token, TokenType.ACCESS_TOKEN));
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (ExpiredJwtException e) {
            SecurityContextHolder.clearContext();
            log.info("[Auth] Access 토큰 만료 - {}", request.getRequestURI());
            authErrorWriter.write(response, AuthErrorCode.ACCESS_TOKEN_EXPIRED); // 프론트는 이걸 보면 재발급
            return;
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            log.warn("[Auth] 잘못된 토큰 - {}", e.getClass().getSimpleName());
            authErrorWriter.write(response, AuthErrorCode.INVALID_TOKEN);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
