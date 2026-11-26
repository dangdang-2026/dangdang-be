package com.dangdang.domain.admin;

import com.dangdang.domain.user.entity.User;
import com.dangdang.support.ReminderTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 운영자 API는 허용한 IP에서만 (be #181). 운영 서버는 Nginx 뒤라 Nginx가 넘긴 IP로 판단한다
@TestPropertySource(properties = "admin.allowed-ips=203.0.113.10, 198.51.100.7")
class AdminIpFlowTest extends ReminderTestBase {

    private static final String ALLOWED = "203.0.113.10";
    private static final String OTHER = "192.0.2.55";
    private static final String NGINX = "127.0.0.1";

    private Cookie admin;

    @BeforeEach
    void makeAdmin() throws Exception {
        userRepository.save(User.ofEmail("admin@x.com", "당당 운영진", passwordEncoder.encode("abcd1234"), Instant.now()));
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = 'admin@x.com'");
        admin = login("admin@x.com");
    }

    private MockHttpServletRequestBuilder adminMe(String remoteAddr) {
        return get("/api/admin/me").cookie(admin).with(req -> {
            req.setRemoteAddr(remoteAddr);
            return req;
        });
    }

    @Test
    void 허용한_IP면_운영자_API를_쓴다() throws Exception {
        mvc.perform(adminMe(ALLOWED)).andExpect(status().isOk());
        mvc.perform(adminMe("198.51.100.7")).andExpect(status().isOk()); // 쉼표 뒤 빈칸이 있어도 읽는다
    }

    @Test
    void 다른_IP면_운영자여도_403() throws Exception {
        mvc.perform(adminMe(OTHER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUTH_40302"));
    }

    @Test
    void Nginx를_거치면_X_Real_IP로_판단한다() throws Exception {
        mvc.perform(adminMe(NGINX).header("X-Real-IP", ALLOWED)).andExpect(status().isOk());
        mvc.perform(adminMe(NGINX).header("X-Real-IP", OTHER)).andExpect(status().isForbidden());
    }

    @Test
    void X_Forwarded_For는_Nginx가_마지막에_붙인_값을_쓴다() throws Exception {
        // 앞쪽은 보낸 사람이 꾸밀 수 있다. 허용 IP를 앞에 적어 보내도 막는다
        mvc.perform(adminMe(NGINX).header("X-Forwarded-For", ALLOWED + ", " + OTHER))
                .andExpect(status().isForbidden());
        mvc.perform(adminMe(NGINX).header("X-Forwarded-For", OTHER + ", " + ALLOWED))
                .andExpect(status().isOk());
    }

    @Test
    void 바로_앞이_공인_IP면_헤더를_믿지_않는다() throws Exception {
        mvc.perform(adminMe(OTHER).header("X-Real-IP", ALLOWED))
                .andExpect(status().isForbidden());
    }

    @Test
    void 로그인과_앱_API는_IP를_보지_않는다() throws Exception {
        mvc.perform(post("/api/auth/login").with(req -> {
                            req.setRemoteAddr(OTHER);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@x.com\",\"password\":\"abcd1234\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me").cookie(login("jisu@x.com")).with(req -> {
            req.setRemoteAddr(OTHER);
            return req;
        })).andExpect(status().isOk());
    }

    @Test
    void 막힌_응답에도_CORS_헤더가_붙어_화면이_이유를_읽는다() throws Exception {
        mvc.perform(adminMe(OTHER).header(HttpHeaders.ORIGIN, "http://localhost:5180"))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5180"));
        // 사전 확인(OPTIONS)은 통과시킨다
        mvc.perform(options("/api/admin/me").with(req -> {
                            req.setRemoteAddr(OTHER);
                            return req;
                        })
                        .header(HttpHeaders.ORIGIN, "http://localhost:5180")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk());
    }
}
