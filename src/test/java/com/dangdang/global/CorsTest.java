package com.dangdang.global;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CorsTest {
    @Autowired MockMvc mvc;

    private ResultActions preflight(String origin) throws Exception {
        return mvc.perform(options("/api/auth/login") // 브라우저가 진짜 요청 전에 보내는 사전 확인
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"));
    }

    @Test
    void 허락한_프론트_주소는_통과() throws Exception {
        preflight("http://localhost:5173")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void 모르는_주소는_막는다() throws Exception {
        preflight("https://evil.example")
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    // ---- 운영자 화면은 배포하지 않고 운영진 PC(localhost:5180)에서만 띄운다 ----

    private ResultActions adminPreflight(String origin) throws Exception {
        return mvc.perform(options("/api/admin/reports")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"));
    }

    @Test
    void 운영자_API는_운영자_화면_주소만_통과() throws Exception {
        adminPreflight("http://localhost:5180")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5180"));
    }

    @Test
    void 앱_주소에서는_운영자_API를_막는다() throws Exception {
        adminPreflight("http://localhost:5173")
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void 운영자_화면도_로그인_API는_부를_수_있다() throws Exception {
        preflight("http://localhost:5180")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5180"));
    }
}
