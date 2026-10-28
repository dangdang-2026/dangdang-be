package com.dangdang.domain.auth;

import com.dangdang.domain.auth.infra.EmailSender;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetFlowTest {

    // 진짜 메일 대신: 보낸 링크를 이메일별로 적어 둔다
    static final Map<String, String> SENT = new HashMap<>();

    @TestConfiguration
    static class FakeMail {
        @Bean
        @Primary
        EmailSender fakeEmailSender() {
            return new EmailSender() {
                @Override
                public void sendVerificationCode(String email, String code) {}

                @Override
                public void sendPasswordResetLink(String email, String link) {
                    SENT.put(email, link);
                }
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void member() {
        userRepository.save(User.ofEmail("jisu@x.com", "지수", passwordEncoder.encode("abcd1234"), Instant.now()));
    }

    @AfterEach
    void clean() {
        SENT.clear();
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private ResultActions request(String email) throws Exception {
        return mvc.perform(post("/api/auth/password/reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions confirm(String token, String newPassword) throws Exception {
        return mvc.perform(post("/api/auth/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}"));
    }

    private ResultActions login(String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"jisu@x.com\",\"password\":\"" + password + "\"}"));
    }

    private String tokenOf(String email) {
        String link = SENT.get(email);
        assertThat(link).startsWith("http://localhost:5173/reset-password?token=");
        return link.substring(link.indexOf("token=") + 6);
    }

    @Test
    void 링크로_새_비밀번호를_정하면_새_비밀번호로만_로그인된다() throws Exception {
        request("Jisu@X.com").andExpect(status().isOk());
        confirm(tokenOf("jisu@x.com"), "newpass123").andExpect(status().isOk());

        login("abcd1234").andExpect(status().isBadRequest());
        login("newpass123").andExpect(status().isOk());
    }

    @Test
    void 없는_이메일과_카카오_계정도_똑같이_200이고_메일은_안_간다() throws Exception {
        userRepository.save(User.ofKakao("12345", "민지", "minji@x.com"));
        request("nobody@x.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("가입된 이메일이면 재설정 링크를 보냈어요."));
        request("minji@x.com").andExpect(status().isOk());
        assertThat(SENT).isEmpty();
    }

    @Test
    void 링크는_한_번만_쓸_수_있다() throws Exception {
        request("jisu@x.com");
        String token = tokenOf("jisu@x.com");
        confirm(token, "newpass123").andExpect(status().isOk());
        confirm(token, "other1234")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH_40008"));
    }

    @Test
    void 새_링크를_받으면_예전_링크는_못_쓴다() throws Exception {
        request("jisu@x.com");
        String old = tokenOf("jisu@x.com");
        request("jisu@x.com");
        confirm(old, "newpass123").andExpect(jsonPath("$.errorCode").value("AUTH_40008"));
        confirm(tokenOf("jisu@x.com"), "newpass123").andExpect(status().isOk());
    }

    @Test
    void 이전과_같은_비밀번호는_안_되고_링크는_그대로_다시_쓸_수_있다() throws Exception {
        request("jisu@x.com");
        String token = tokenOf("jisu@x.com");
        confirm(token, "abcd1234")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH_40009"));
        confirm(token, "newpass123").andExpect(status().isOk());
    }

    @Test
    void 비밀번호를_바꾸면_다른_기기의_Refresh도_못_쓴다() throws Exception {
        Cookie refresh = login("abcd1234").andReturn().getResponse().getCookie("REFRESH_TOKEN");
        request("jisu@x.com");
        confirm(tokenOf("jisu@x.com"), "newpass123");

        mvc.perform(post("/api/auth/refresh").cookie(refresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_40103"));
    }

    @Test
    void 잠긴_계정도_비밀번호를_바꾸면_바로_로그인된다() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("wrong1234");
        }
        login("abcd1234").andExpect(status().isTooManyRequests());
        request("jisu@x.com");
        confirm(tokenOf("jisu@x.com"), "newpass123");
        login("newpass123").andExpect(status().isOk());
    }

    @Test
    void 같은_이메일로_하루_5번_넘게_요청하면_429() throws Exception {
        for (int i = 0; i < 5; i++) {
            request("nobody@x.com").andExpect(status().isOk());
        }
        request("nobody@x.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("AUTH_42903"));
    }

    @Test
    void 이상한_토큰은_만료와_같은_400() throws Exception {
        confirm("made-up-token", "newpass123").andExpect(jsonPath("$.errorCode").value("AUTH_40008"));
    }
}
