package com.dangdang.global.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

// 접속 로그 필터가 요청을 한 줄로 남기는지 (be #193)
class AccessLogFilterTest {

    private final AccessLogFilter filter = new AccessLogFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(AccessLogFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attach() {
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    @Test
    void 요청을_한_줄로_남긴다() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/foods/search");
        req.setRemoteAddr("203.0.113.9");
        MockHttpServletResponse res = new MockHttpServletResponse();
        res.setStatus(200);

        filter.doFilter(req, res, new MockFilterChain());

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage())
                .startsWith("ACCESS 203.0.113.9 GET /api/foods/search -> 200");
    }

    @Test
    void Nginx_뒤면_X_Real_IP를_쓴다() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setRemoteAddr("127.0.0.1");                 // Nginx가 붙으면 스프링엔 127.0.0.1이 찍힌다
        req.addHeader("X-Real-IP", "198.51.100.7");
        MockHttpServletResponse res = new MockHttpServletResponse();
        res.setStatus(401);

        filter.doFilter(req, res, new MockFilterChain());

        assertThat(appender.list.get(0).getFormattedMessage())
                .startsWith("ACCESS 198.51.100.7 POST /api/auth/login -> 401");
    }

    @Test
    void actuator_헬스체크는_안_남긴다() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        req.setRemoteAddr("203.0.113.9");

        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(appender.list).isEmpty();
    }
}
