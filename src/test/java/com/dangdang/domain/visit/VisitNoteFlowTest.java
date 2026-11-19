package com.dangdang.domain.visit;

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
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 진료 메모 흐름 테스트
@SpringBootTest
@AutoConfigureMockMvc
class VisitNoteFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    Cookie access;   // 지수(주인공)
    Cookie other;    // 남의 일정 확인용 다른 사용자
    long visitId;

    @BeforeEach
    void setUp() throws Exception {
        access = signupAndLogin("jisu@x.com");
        other = signupAndLogin("minho@x.com");
        visitId = createVisit(access);
    }

    @AfterEach
    void clean() { // users를 지우면 visits·visit_notes도 같이 지워짐 (ON DELETE CASCADE)
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void 메모를_적은_그대로_저장한다() throws Exception {
        String content = "  공복 95 넘으면 연락하래요.\\n다음 진료 2주 뒤  ";
        add(access, visitId, "{\"content\":\"" + content + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.noteId").exists())
                .andExpect(jsonPath("$.data.content").value("  공복 95 넘으면 연락하래요.\n다음 진료 2주 뒤  "))   // 공백·줄바꿈 그대로
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.updatedAt").exists());
    }

    @Test
    void 조회는_먼저_쓴_순이고_일정_상세에도_나온다() throws Exception {
        long first = addAndGetId("{\"content\":\"첫 메모\"}");
        long second = addAndGetId("{\"content\":\"둘째 메모\"}");

        list(access, visitId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].noteId").value(contains((int) first, (int) second)));
        mvc.perform(get("/api/visits/" + visitId).cookie(access))
                .andExpect(jsonPath("$.data.notes", hasSize(2)))
                .andExpect(jsonPath("$.data.notes[0].content").value("첫 메모"));
    }

    @Test
    void 메모를_고치면_내용이_통째로_바뀐다() throws Exception {
        long note = addAndGetId("{\"content\":\"처음 내용\"}");

        update(access, visitId, note, "{\"content\":\"고친 내용\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.noteId").value(note))
                .andExpect(jsonPath("$.data.content").value("고친 내용"));
        list(access, visitId).andExpect(jsonPath("$.data[0].content").value("고친 내용"));
    }

    @Test
    void 내용이_비었거나_너무_길면_400() throws Exception {
        add(access, visitId, "{\"content\":\"  \"}").andExpect(status().isBadRequest());
        add(access, visitId, "{}").andExpect(status().isBadRequest());
        add(access, visitId, "{\"content\":\"" + "가".repeat(5001) + "\"}").andExpect(status().isBadRequest());
        add(access, visitId, "{\"content\":\"" + "가".repeat(5000) + "\"}").andExpect(status().isCreated());

        long note = addAndGetId("{\"content\":\"메모\"}");
        update(access, visitId, note, "{\"content\":\"\"}").andExpect(status().isBadRequest());
    }

    @Test
    void 다른_일정의_메모는_못_고친다() throws Exception {
        long note = addAndGetId("{\"content\":\"메모\"}");
        long anotherVisit = createVisit(access);

        update(access, anotherVisit, note, "{\"content\":\"고침\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("VISIT_40403"));
        update(access, visitId, 999999999L, "{\"content\":\"고침\"}").andExpect(status().isNotFound());
    }

    @Test
    void 남의_일정이나_지운_일정이면_404() throws Exception {
        long othersVisit = createVisit(other);
        list(access, othersVisit)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("VISIT_40401"));
        add(access, othersVisit, "{\"content\":\"메모\"}").andExpect(status().isNotFound());

        long note = addAndGetId("{\"content\":\"메모\"}");
        update(other, visitId, note, "{\"content\":\"남이 고침\"}").andExpect(status().isNotFound());

        mvc.perform(delete("/api/visits/" + visitId).cookie(access)).andExpect(status().isOk());
        list(access, visitId).andExpect(status().isNotFound());
        update(access, visitId, note, "{\"content\":\"고침\"}").andExpect(status().isNotFound());
    }

    // ---------- 도우미 ----------

    private Cookie signupAndLogin(String email) throws Exception {
        userRepository.save(User.ofEmail(email, "테스트", passwordEncoder.encode("abcd1234"), Instant.now()));
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    private long createVisit(Cookie who) throws Exception {
        String body = mvc.perform(post("/api/visits").cookie(who)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.visitId")).longValue();
    }

    private ResultActions add(Cookie who, long visit, String json) throws Exception {
        return mvc.perform(post("/api/visits/" + visit + "/notes").cookie(who)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long addAndGetId(String json) throws Exception {
        String body = add(access, visitId, json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.noteId")).longValue();
    }

    private ResultActions list(Cookie who, long visit) throws Exception {
        return mvc.perform(get("/api/visits/" + visit + "/notes").cookie(who));
    }

    private ResultActions update(Cookie who, long visit, long note, String json) throws Exception {
        return mvc.perform(patch("/api/visits/" + visit + "/notes/" + note).cookie(who)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
