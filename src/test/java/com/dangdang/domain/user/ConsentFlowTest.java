package com.dangdang.domain.user;

import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ConsentFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    Cookie access;

    @BeforeEach
      void loginFirst() throws Exception {
        userRepository.save(User.ofEmail("jisu@x.com", "지수", passwordEncoder.encode("abcd1234"), Instant.now()));
        access = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jisu@x.com\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    @AfterEach
    void clean() { // users를 지우면 consent_events도 같이 지워진다 (ON DELETE CASCADE)
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private static final String A01 = """
            {"items":[
              {"type":"AGE_14","granted":true},{"type":"TERMS","granted":true},
              {"type":"PRIVACY","granted":true},{"type":"SENSITIVE","granted":true},
              {"type":"MARKETING","granted":false}]}
            """;

    @Test
    void 가입_직후엔_동의_전이라_consentCompleted가_false() throws Exception {
        mvc.perform(get("/api/me").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consentCompleted").value(false));
    }

    @Test
    void 필수_4개에_동의하면_완료되고_버전은_서버가_넣는다() throws Exception {
        mvc.perform(post("/api/me/consents").cookie(access).contentType(MediaType.APPLICATION_JSON).content(A01))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(8))
                .andExpect(jsonPath("$.data.items[?(@.type=='TERMS')].version").value("v1"))
                .andExpect(jsonPath("$.data.items[?(@.type=='AI_PHOTO')].granted").value(false));

        mvc.perform(get("/api/me").cookie(access))
                .andExpect(jsonPath("$.data.consentCompleted").value(true));
    }

    @Test
    void 필수_항목을_false로_보내면_400() throws Exception {
        mvc.perform(post("/api/me/consents").cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"type\":\"SENSITIVE\",\"granted\":false}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("USER_40001"));
    }

    @Test
    void 선택_항목은_나중에_하나만_켜고_끌_수_있고_최신_것이_보인다() throws Exception {
        mvc.perform(post("/api/me/consents").cookie(access).contentType(MediaType.APPLICATION_JSON).content(A01));
        mvc.perform(post("/api/me/consents").cookie(access).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"type\":\"AI_PHOTO\",\"granted\":true}]}"));
        mvc.perform(post("/api/me/consents").cookie(access).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"type\":\"AI_PHOTO\",\"granted\":false}]}"));

        mvc.perform(get("/api/me/consents").cookie(access))
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.items[?(@.type=='AI_PHOTO')].granted").value(false));
    }

    @Test
    void 모르는_항목_이름은_500이_아니라_400() throws Exception {
        mvc.perform(post("/api/me/consents").cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"type\":\"HELLO\",\"granted\":true}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
    }

    @Test
    void 토큰_없이_동의하면_401() throws Exception {
        mvc.perform(post("/api/me/consents").contentType(MediaType.APPLICATION_JSON).content(A01))
                .andExpect(status().isUnauthorized());
    }
}