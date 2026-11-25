package com.dangdang.domain.post;

import com.dangdang.domain.post.infra.PostCheckClient;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PostPrecheckFlowTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean PostCheckClient postCheckClient;   // 진짜 AI 서버 대신

    Cookie jisu;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.save(User.ofEmail("jisu@x.com", "지수", passwordEncoder.encode("abcd1234"), Instant.now()));
        jisu = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jisu@x.com\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private String body(String title, String content) {
        return "{\"title\":\"" + title + "\",\"content\":\"" + content + "\"}";
    }

    @Test
    void AI가_찾은_문장을_그대로_돌려준다() throws Exception {
        given(postCheckClient.check(any(), any(), anyBoolean())).willReturn(new PostCheckClient.Result(List.of(
                new PostCheckClient.Issue("DOSE", "지난주부터 인슐린 8단위로 늘렸는데 다들 몇 단위 맞으세요?")), true));

        mvc.perform(post("/api/posts/precheck").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content(body("식후 1시간 알림", "지난주부터 인슐린 8단위로 늘렸는데 다들 몇 단위 맞으세요?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checked").value(true))
                .andExpect(jsonPath("$.data.issues[0].kind").value("DOSE"))
                .andExpect(jsonPath("$.data.issues[0].quote").value("지난주부터 인슐린 8단위로 늘렸는데 다들 몇 단위 맞으세요?"));
    }

    @Test
    void 걸린_게_없으면_빈_목록() throws Exception {
        given(postCheckClient.check(any(), any(), anyBoolean())).willReturn(new PostCheckClient.Result(List.of(), false));

        mvc.perform(post("/api/posts/precheck").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content(body("아침 뭐 드세요?", "공복 혈당 때문에 고민이에요.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checked").value(true))
                .andExpect(jsonPath("$.data.issues").isEmpty());
    }

    @Test
    void AI_서버가_죽어도_200이고_글쓰기를_막지_않는다() throws Exception {
        given(postCheckClient.check(any(), any(), anyBoolean())).willThrow(new ResourceAccessException("연결 거부"));

        mvc.perform(post("/api/posts/precheck").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content(body("제목", "인슐린 8단위")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checked").value(false))
                .andExpect(jsonPath("$.data.issues").isEmpty());
    }

    @Test
    void 제목은_비워도_된다() throws Exception {
        given(postCheckClient.check(any(), any(), anyBoolean())).willReturn(new PostCheckClient.Result(List.of(), false));

        mvc.perform(post("/api/posts/precheck").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"본문만 있어요\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 본문이_비면_400이고_AI를_부르지_않는다() throws Exception {
        mvc.perform(post("/api/posts/precheck").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content(body("제목", " ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        verify(postCheckClient, never()).check(any(), any(), anyBoolean());
    }

    @Test
    void 커뮤니티_AI_확인에_동의해야_GPT까지_본다() throws Exception {
        given(postCheckClient.check(any(), any(), anyBoolean())).willReturn(new PostCheckClient.Result(List.of(), false));
        mvc.perform(post("/api/posts/precheck").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                .content(body("제목", "인슐린 4단위"))).andExpect(status().isOk());
        verify(postCheckClient).check(any(), any(), eq(false));

        mvc.perform(post("/api/me/consents").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"type\":\"COMMUNITY_AI_CHECK\",\"granted\":true}]}")).andExpect(status().isOk());
        mvc.perform(post("/api/posts/precheck").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                .content(body("제목", "인슐린 4단위"))).andExpect(status().isOk());
        verify(postCheckClient).check(any(), any(), eq(true));
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mvc.perform(post("/api/posts/precheck").contentType(MediaType.APPLICATION_JSON)
                        .content(body("제목", "본문")))
                .andExpect(status().isUnauthorized());
    }
}
