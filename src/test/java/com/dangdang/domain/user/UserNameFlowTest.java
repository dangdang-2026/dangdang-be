package com.dangdang.domain.user;

import com.dangdang.domain.user.dto.request.NameUpdateRequest;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.domain.user.service.UserNameService;
import com.dangdang.global.exception.CustomException;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UserNameFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserNameService userNameService;
    @Autowired TransactionTemplate tx;

    Cookie access;
    Long userId;

    @BeforeEach
    void loginFirst() throws Exception {
        userId = userRepository
                .save(User.ofEmail("jisu@x.com", "지수", passwordEncoder.encode("abcd1234"), Instant.now()))
                .getId();
        access = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jisu@x.com\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private ResultActions change(String body) throws Exception {
        return mvc.perform(patch("/api/me").cookie(access)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String savedName() {
        return jdbc.queryForObject("SELECT display_name FROM users WHERE id = ?", String.class, userId);
    }

    @Test
    void 내_정보의_version으로_바꾸면_앞뒤_공백이_지워지고_version이_오른다() throws Exception {
        mvc.perform(get("/api/me").cookie(access))
                .andExpect(jsonPath("$.data.version").value("0"));

        change("{\"name\":\"  승민  \",\"version\":\"0\"}")
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("승민"))
                .andExpect(jsonPath("$.data.version").value("1"));

        assertThat(savedName()).isEqualTo("승민");
        mvc.perform(get("/api/me").cookie(access))
                .andExpect(jsonPath("$.data.displayName").value("승민"))
                .andExpect(jsonPath("$.data.version").value("1"));
    }

    @Test
    void 열_글자까지_되고_이모지도_한_글자로_센다() throws Exception {
        change("{\"name\":\"가나다라마바사아자차\",\"version\":\"0\"}")
                .andExpect(status().isOk());
        String tenEmoji = "😀".repeat(10);
        change("{\"name\":\"" + tenEmoji + "\",\"version\":\"1\"}")
                .andExpect(status().isOk());
        assertThat(savedName()).isEqualTo(tenEmoji);
    }

    @Test
    void 이름이_없거나_비었거나_열한_글자면_400() throws Exception {
        for (String body : List.of(
                "{\"version\":\"0\"}",
                "{\"name\":null,\"version\":\"0\"}",
                "{\"name\":\"\",\"version\":\"0\"}",
                "{\"name\":\"   \",\"version\":\"0\"}",
                "{\"name\":\"가나다라마바사아자차카\",\"version\":\"0\"}",
                "{\"name\":\"가\\u0000나\",\"version\":\"0\"}")) {
            change(body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        }
        assertThat(savedName()).isEqualTo("지수");
    }

    @Test
    void version이_없거나_숫자가_아니면_400() throws Exception {
        for (String body : List.of(
                "{\"name\":\"승민\"}",
                "{\"name\":\"승민\",\"version\":null}",
                "{\"name\":\"승민\",\"version\":\"\"}",
                "{\"name\":\"승민\",\"version\":\"abc\"}",
                "{\"name\":\"승민\",\"version\":\"-1\"}",
                "{\"name\":\"승민\",\"version\":\"1234567890123456789012\"}")) {
            change(body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        }
    }

    @Test
    void version이_다르면_409이고_이름은_그대로다() throws Exception {
        change("{\"name\":\"승민\",\"version\":\"7\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USER_40901"));
        assertThat(savedName()).isEqualTo("지수");
    }

    @Test
    void 비교를_통과한_뒤_저장_순간에_어긋나도_409다() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            User loaded = userRepository.findById(userId).orElseThrow();   // 이 요청이 먼저 읽고
            jdbc.update("UPDATE users SET row_version = row_version + 1 WHERE id = ?", userId);   // 다른 요청이 먼저 저장
            userNameService.change(userId, new NameUpdateRequest("승민", String.valueOf(loaded.getRowVersion())));
        }))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("먼저 고쳤어요");
        assertThat(savedName()).isEqualTo("지수");
    }

    @Test
    void 같은_이름으로_보내면_200이고_version은_그대로다() throws Exception {
        change("{\"name\":\"지수\",\"version\":\"0\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value("0"));
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mvc.perform(patch("/api/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"승민\",\"version\":\"0\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_40101"));
    }
}
