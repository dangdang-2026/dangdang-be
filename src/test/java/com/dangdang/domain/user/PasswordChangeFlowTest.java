package com.dangdang.domain.user;

import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.security.AuthPrincipal;
import com.dangdang.global.security.jwt.JwtProvider;
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
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordChangeFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtProvider jwtProvider;

    @BeforeEach
    void members() {
        userRepository.save(User.ofEmail("jisu@x.com", "지수", passwordEncoder.encode("abcd1234"), Instant.now()));
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private MvcResult login(String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jisu@x.com\",\"password\":\"" + password + "\"}"))
                .andReturn();
    }

    private ResultActions change(Cookie access, String current, String next) throws Exception {
        return mvc.perform(patch("/api/me/password")
                .cookie(access)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    @Test
    void 바꾸면_다른_기기는_로그아웃되고_지금_기기는_새_쿠키를_받는다() throws Exception {
        MvcResult phone = login("abcd1234");   // 다른 기기
        MvcResult laptop = login("abcd1234");  // 지금 기기

        MvcResult changed = change(laptop.getResponse().getCookie("ACCESS_TOKEN"), "abcd1234", "newpass123")
                .andExpect(status().isOk())
                .andReturn();
        assertThat(changed.getResponse().getCookie("REFRESH_TOKEN")).isNotNull();

        // 다른 기기의 Refresh는 죽었다
        mvc.perform(post("/api/auth/refresh").cookie(phone.getResponse().getCookie("REFRESH_TOKEN")))
                .andExpect(status().isUnauthorized());
        // 지금 기기는 새로 받은 Refresh로 계속 쓴다
        mvc.perform(post("/api/auth/refresh").cookie(changed.getResponse().getCookie("REFRESH_TOKEN")))
                .andExpect(status().isOk());
        // 새 비밀번호로만 로그인된다
        assertThat(login("abcd1234").getResponse().getStatus()).isEqualTo(400);
        assertThat(login("newpass123").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void 지금_비밀번호가_틀리면_USER_40003() throws Exception {
        Cookie access = login("abcd1234").getResponse().getCookie("ACCESS_TOKEN");
        change(access, "wrong1234", "newpass123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("USER_40003"));
    }

    @Test
    void 이전과_같으면_AUTH_40009() throws Exception {
        Cookie access = login("abcd1234").getResponse().getCookie("ACCESS_TOKEN");
        change(access, "abcd1234", "abcd1234")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH_40009"));
    }

    @Test
    void 새_비밀번호_규칙이_틀리면_GLOBAL_40001() throws Exception {
        Cookie access = login("abcd1234").getResponse().getCookie("ACCESS_TOKEN");
        change(access, "abcd1234", "1234")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mvc.perform(patch("/api/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"abcd1234\",\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_40101"));
    }

    @Test
    void 카카오_계정은_USER_40002() throws Exception {
        User kakao = userRepository.save(User.ofKakao("12345", "민지", null));
        Cookie access = new Cookie("ACCESS_TOKEN", jwtProvider.createAccessToken(AuthPrincipal.from(kakao)));
        change(access, "abcd1234", "newpass123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("USER_40002"));
    }
}