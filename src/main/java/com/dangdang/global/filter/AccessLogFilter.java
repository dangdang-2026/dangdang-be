package com.dangdang.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 들어오는 모든 요청을 한 줄씩 남긴다 → app 컨테이너 로그로 나가 Dozzle(log.dang-dang.site)에서 바로 본다 (be #193)
// IP 판단은 AdminIpFilter.clientIp()와 같은 규칙(Nginx X-Real-IP 신뢰)을 쓴다 (be #181)
// 쿼리스트링·헤더·바디는 남기지 않는다. getRequestURI()는 ? 뒤를 빼므로 URL에 실린 토큰이 안 찍힌다
@Slf4j
public class AccessLogFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 1초마다 때리는 헬스체크와 에러 분기는 로그를 더럽히니 뺀다
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator") || uri.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            // finally라서 보안 필터가 401/403으로 끊어도 최종 상태코드가 찍힌다
            log.info("ACCESS {} {} {} -> {} ({}ms)",
                    AdminIpFilter.clientIp(request),
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    System.currentTimeMillis() - start);
        }
    }
}
