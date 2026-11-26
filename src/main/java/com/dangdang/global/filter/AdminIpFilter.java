package com.dangdang.global.filter;

import com.dangdang.domain.auth.exception.AuthErrorCode;
import com.dangdang.global.config.property.AdminIpProperties;
import com.dangdang.global.security.AuthErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

// 운영자 API(/api/admin/**)는 허용한 IP에서 온 요청만 받는다 (be #181)
// CORS는 브라우저만 막아서, 서버 쪽에서 한 번 더 막는다. 로그인(/api/auth/**)은 앱도 쓰므로 막지 않는다
// @Component로 두지 않는다. 그러면 서블릿 필터로도 한 번 더 등록되기 때문에 SecurityConfig에서만 넣는다
@Slf4j
@RequiredArgsConstructor
public class AdminIpFilter extends OncePerRequestFilter {

    private final AdminIpProperties properties;
    private final AuthErrorWriter authErrorWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled()
                || !request.getRequestURI().startsWith("/api/admin/")
                // 브라우저의 사전 확인(OPTIONS)은 통과시킨다. 본 요청이 403 JSON을 받아야 화면이 이유를 보여 줄 수 있다
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = clientIp(request);
        if (!properties.allowedIps().contains(ip)) {
            log.warn("허용하지 않은 IP의 운영자 API 요청: ip={} uri={}", ip, request.getRequestURI());
            authErrorWriter.write(response, AuthErrorCode.ADMIN_IP_NOT_ALLOWED);
            return;
        }
        chain.doFilter(request, response);
    }

    // 운영 서버는 Nginx 뒤에 있어서 Spring에는 Nginx 주소(127.0.0.1)가 찍힌다
    // 바로 앞이 내부 주소(Nginx)일 때만 Nginx가 넘긴 헤더를 믿는다. 밖에서 헤더를 꾸며 보내도 바로 앞이 공인 IP면 무시한다
    static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!isInternal(remote)) {
            return remote;
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.strip();
        }
        // X-Forwarded-For는 「처음 보낸 쪽, 거친 프록시들...」 순서. 앞쪽은 보낸 사람이 꾸밀 수 있어서
        // 우리 Nginx가 마지막에 붙인 값(바로 앞에서 Nginx에 들어온 주소)을 쓴다
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            return parts[parts.length - 1].strip();
        }
        return remote;
    }

    private static boolean isInternal(String ip) {
        // getRemoteAddr는 항상 IP 글자라 이름 조회(DNS)는 일어나지 않는다
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isLoopbackAddress() || address.isSiteLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
