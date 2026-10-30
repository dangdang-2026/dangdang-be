package com.dangdang.domain.auth;

import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EmailAvailabilityFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void members() {
        userRepository.save(User.ofEmail("jisu@x.com", "지수", "hash", Instant.now()));
        userRepository.save(User.ofKakao("12345", "민지", "minji@x.com"));
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM users");
    }

    private ResultActions check(String email) throws Exception {
        return mvc.perform(post("/api/auth/email/availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    @Test
    void 처음_보는_이메일은_쓸_수_있다() throws Exception {
        check("new@x.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.authProvider").doesNotExist());
    }

    @Test
    void 이메일로_가입한_이메일은_EMAIL() throws Exception {
        check("Jisu@X.com") // 대문자로 적어도 같은 이메일
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.authProvider").value("EMAIL"));
    }

    @Test
    void 카카오로_가입한_이메일은_KAKAO() throws Exception {
        check("minji@x.com")
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.authProvider").value("KAKAO"));
    }

    @Test
    void 이메일_형식이_아니면_400() throws Exception {
        check("not-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
    }
}