package com.dangdang.domain.reading;

import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.config.ClockConfig;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ReadingFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    Cookie access;   // 지수(주인공)
    Cookie other;    // 남의 기록 확인용 다른 사용자

    @BeforeEach
    void loginFirst() throws Exception {
        access = signupAndLogin("jisu@x.com");
        other = signupAndLogin("minho@x.com");
    }

    @AfterEach
    void clean() { // users를 지우면 readings·criteria_versions도 같이 지워짐 (ON DELETE CASCADE)
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private Cookie signupAndLogin(String email) throws Exception {
        userRepository.save(User.ofEmail(email, "테스트", passwordEncoder.encode("abcd1234"), Instant.now()));
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    // 등록하고 readingId 돌려줌 (201 기대)
    private long create(int value, Instant measuredAt) throws Exception {
        String body = mvc.perform(post("/api/readings").cookie(access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(readingJson(value, measuredAt, "FASTING")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.readingId")).longValue();
    }

    private static String readingJson(int value, Instant measuredAt, String type) {
        return "{\"value\":" + value + ",\"measuredAt\":\"" + measuredAt + "\",\"readingType\":\"" + type + "\"}";
    }

    // 한국 날짜 그날의 hh:mm 시각
    private static Instant kst(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(ClockConfig.KST).toInstant();
    }

    private static LocalDate today() {
        return LocalDate.now(ClockConfig.KST);
    }

    // ---------- 등록 ----------

    @Test
    void 방금_잰_값을_등록하면_201이고_늦게_적음이_아니다() throws Exception {
        mvc.perform(post("/api/readings").cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content(readingJson(95, Instant.now(), "FASTING")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.value").value(95))
                .andExpect(jsonPath("$.data.criteriaVersionId").exists())   // 기준이 없어도 빈 기준이 붙음
                .andExpect(jsonPath("$.data.enteredLate").value(false));
    }

    @Test
    void 삼십분_넘게_지나서_적으면_늦게_적음() throws Exception {
        mvc.perform(post("/api/readings").cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content(readingJson(140, Instant.now().minus(Duration.ofMinutes(31)), "POST_MEAL")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.enteredLate").value(true));
    }

    @Test
    void 범위_밖이거나_미래_시각이면_400() throws Exception {
        for (String body : List.of(
                readingJson(19, Instant.now(), "FASTING"),
                readingJson(601, Instant.now(), "FASTING"),
                readingJson(100, Instant.now().plus(Duration.ofDays(1)), "FASTING"))) {
            mvc.perform(post("/api/readings").cookie(access)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        }
    }

    // ---------- 상세 ----------

    @Test
    void 남의_기록이나_없는_기록은_404() throws Exception {
        long id = create(95, Instant.now());
        mvc.perform(get("/api/readings/" + id).cookie(other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("READING_40401"));
        mvc.perform(get("/api/readings/" + (id + 999)).cookie(access))
                .andExpect(status().isNotFound());
    }

    // ---------- 목록 ----------

    @Test
    void 목록은_적은_순서가_아니라_잰_시각_최신순() throws Exception {
        LocalDate day = today().minusDays(1);
        long morning = create(90, kst(day, 8, 0));
        long night = create(120, kst(day, 21, 0));
        long lunch = create(150, kst(day, 13, 0));   // 늦게 적어서 id는 제일 크지만 시각은 중간

        mvc.perform(get("/api/readings").cookie(access)
                        .param("from", day.toString()).param("to", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].readingId").value(night))
                .andExpect(jsonPath("$.data.items[1].readingId").value(lunch))
                .andExpect(jsonPath("$.data.items[2].readingId").value(morning))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursorAt").doesNotExist());
    }

    @Test
    void 커서로_끝까지_넘기면_빠짐도_겹침도_없다() throws Exception {
        LocalDate day = today().minusDays(1);
        Instant sameTime = kst(day, 12, 0);
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.add(create(100 + i, sameTime));   // 잰 시각이 전부 같아도 id로 순서가 정해져야 함
        }

        List<Long> seen = new ArrayList<>();
        String cursorAt = null;
        String cursorId = null;
        boolean hasNext = true;
        while (hasNext) {
            var req = get("/api/readings").cookie(access)
                    .param("from", day.toString()).param("to", day.toString()).param("size", "2");
            if (cursorAt != null) req.param("cursorAt", cursorAt).param("cursorId", cursorId);
            String body = mvc.perform(req).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            List<Number> ids = JsonPath.read(body, "$.data.items[*].readingId");
            ids.forEach(n -> seen.add(n.longValue()));
            hasNext = JsonPath.read(body, "$.data.hasNext");
            if (hasNext) {
                cursorAt = JsonPath.read(body, "$.data.nextCursorAt");
                cursorId = String.valueOf((Number) JsonPath.read(body, "$.data.nextCursorId"));
            }
        }

        assertThat(seen).containsExactlyElementsOf(created.reversed());   // 같은 시각이면 id 큰 것부터
    }

    @Test
    void 기간은_한국_날짜_기준으로_자른다() throws Exception {
        LocalDate day = today().minusDays(2);
        long lastMinute = create(100, kst(day, 23, 59));
        create(110, kst(day.plusDays(1), 0, 0));   // 다음 날 0시는 빠져야 함 (UTC로는 같은 날 15시)

        mvc.perform(get("/api/readings").cookie(access)
                        .param("from", day.toString()).param("to", day.toString()))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].readingId").value(lastMinute));
    }

    @Test
    void 남의_기록은_목록에_안_나온다() throws Exception {
        create(95, Instant.now());
        mvc.perform(get("/api/readings").cookie(other)
                        .param("from", today().toString()).param("to", today().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void 기간이_뒤집히거나_커서를_하나만_보내면_400() throws Exception {
        mvc.perform(get("/api/readings").cookie(access)
                        .param("from", today().toString()).param("to", today().minusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("READING_40001"));
        mvc.perform(get("/api/readings").cookie(access)
                        .param("from", today().toString()).param("to", today().toString())
                        .param("cursorId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("READING_40002"));
    }

    // ---------- 수정 ----------

    @Test
    void 보낸_칸만_바뀌고_나머지는_그대로() throws Exception {
        Instant measuredAt = Instant.now();
        long id = create(95, measuredAt);
        mvc.perform(patch("/api/readings/" + id).cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":110}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value(110))
                .andExpect(jsonPath("$.data.readingType").value("FASTING"))
                .andExpect(jsonPath("$.data.enteredLate").value(false));
    }

    @Test
    void 잰_시각을_앞당기면_늦게_적음이_다시_계산된다() throws Exception {
        long id = create(95, Instant.now());
        mvc.perform(patch("/api/readings/" + id).cookie(access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"measuredAt\":\"" + Instant.now().minus(Duration.ofHours(3)) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enteredLate").value(true));
    }

    @Test
    void 바꿀_값이_없거나_미래_시각이면_400() throws Exception {
        long id = create(95, Instant.now());
        mvc.perform(patch("/api/readings/" + id).cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("READING_40003"));
        mvc.perform(patch("/api/readings/" + id).cookie(access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"measuredAt\":\"" + Instant.now().plus(Duration.ofDays(1)) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
    }

    @Test
    void 남의_기록은_수정할_수_없다() throws Exception {
        long id = create(95, Instant.now());
        mvc.perform(patch("/api/readings/" + id).cookie(other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":110}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/readings/" + id).cookie(access))
                .andExpect(jsonPath("$.data.value").value(95));
    }

    // ---------- 삭제 ----------

    @Test
    void 삭제하면_상세_목록에서_사라지고_DB에는_남는다() throws Exception {
        long id = create(95, Instant.now());
        mvc.perform(delete("/api/readings/" + id).cookie(access))
                .andExpect(status().isOk());

        mvc.perform(get("/api/readings/" + id).cookie(access))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/readings").cookie(access)
                        .param("from", today().toString()).param("to", today().toString()))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM readings WHERE id = ? AND deleted_at IS NOT NULL", Integer.class, id);
        assertThat(rows).isEqualTo(1);   // soft delete: 줄은 남고 deleted_at만 채워짐
    }

    @Test
    void 이미_지운_기록이나_남의_기록은_삭제_404() throws Exception {
        long id = create(95, Instant.now());
        mvc.perform(delete("/api/readings/" + id).cookie(other))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/readings/" + id).cookie(access))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/readings/" + id).cookie(access))
                .andExpect(status().isNotFound());
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mvc.perform(get("/api/readings")
                        .param("from", today().toString()).param("to", today().toString()))
                .andExpect(status().isUnauthorized());
    }
}
