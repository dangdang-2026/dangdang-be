package com.dangdang.domain.auth;

import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LoginFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void member() { // 가입 과정은 2단계에서 시험했으니 바로 저장
        userRepository.save(User.ofEmail("jisu@x.com", "지수", passwordEncoder.encode("abcd1234"), Instant.now()));
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
    }

    @Test
    void 로그인하면_쿠키_두_개가_오고_내_정보를_볼_수_있다() throws Exception {
        MvcResult result = login("Jisu@X.com", "abcd1234");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Cookie access = result.getResponse().getCookie("ACCESS_TOKEN");
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(result.getResponse().getCookie("REFRESH_TOKEN")).isNotNull();

        mvc.perform(get("/api/me").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("jisu@x.com"))
                .andExpect(jsonPath("$.data.displayName").value("지수"));
    }

    @Test
    void 토큰_없이_내_정보를_보면_401() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_40101"));
    }

    @Test
    void 이상한_토큰은_401() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_40103"));
    }

    @Test
    void 비밀번호를_다섯_번_틀리면_맞는_비밀번호도_5분_동안_막힌다() throws Exception {
        for (int i = 1; i <= 4; i++) {
            MvcResult wrong = login("jisu@x.com", "wrong1234");
            assertThat(wrong.getResponse().getStatus()).isEqualTo(400);
            assertThat(wrong.getResponse().getContentAsString()).contains((5 - i) + "번 더 틀리면 5분 동안 잠겨요");
        }
        assertThat(login("jisu@x.com", "wrong1234").getResponse().getContentAsString()).contains("AUTH_42902");
        assertThat(login("jisu@x.com", "abcd1234").getResponse().getStatus()).isEqualTo(429);
    }

    @Test
    void 재발급하면_새_토큰이_오고_쓴_Refresh는_다시_못_쓴다() throws Exception {
        Cookie refresh = login("jisu@x.com", "abcd1234").getResponse().getCookie("REFRESH_TOKEN");

        mvc.perform(post("/api/auth/refresh").cookie(refresh))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("ACCESS_TOKEN"))
                .andExpect(cookie().exists("REFRESH_TOKEN"));

        mvc.perform(post("/api/auth/refresh").cookie(refresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_40103"));
    }

    @Test
    void 로그아웃하면_쿠키가_지워지고_Refresh도_못_쓴다() throws Exception {
        Cookie refresh = login("jisu@x.com", "abcd1234").getResponse().getCookie("REFRESH_TOKEN");

        mvc.perform(post("/api/auth/logout").cookie(refresh))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("REFRESH_TOKEN", 0));

        mvc.perform(post("/api/auth/refresh").cookie(refresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 카카오로_가입한_이메일도_틀린_비밀번호와_같은_답이고_횟수에_들어간다() throws Exception {
        userRepository.save(User.ofKakao("12345", "민지", "minji@x.com"));
        MvcResult r = login("minji@x.com", "abcd1234");
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        String body = r.getResponse().getContentAsString();
        assertThat(body).contains("AUTH_40004").doesNotContain("AUTH_40005").doesNotContain("카카오");
        assertThat(JsonPath.<Integer>read(body, "$.data.remainingAttempts")).isEqualTo(4);
    }

    @Test
    void 잠기면_풀릴_때까지_남은_초를_준다() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("jisu@x.com", "wrong1234");
        }
        String body = login("jisu@x.com", "abcd1234").getResponse().getContentAsString();
        assertThat(body).contains("AUTH_42902");
        assertThat(JsonPath.<Integer>read(body, "$.data.retryAfterSeconds")).isBetween(1, 300);
    }

    @Test   // 합치며 추가 (76 D 가드 확인)
    void 비밀번호가_72바이트를_넘어도_500이_아니라_같은_400() throws Exception {
        MvcResult r = login("jisu@x.com", "가".repeat(25));   // 한글 25자 = 75바이트
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(r.getResponse().getContentAsString()).contains("AUTH_40004");
    }
}
