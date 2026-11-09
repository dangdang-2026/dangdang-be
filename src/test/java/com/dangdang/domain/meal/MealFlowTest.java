package com.dangdang.domain.meal;

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
class MealFlowTest {
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
    void clean() { // users를 지우면 meals도 같이 지워짐 (ON DELETE CASCADE)
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

    // 등록하고 mealId 돌려줌 (201 기대)
    private long create(String slot, Instant startedAt) throws Exception {
        String body = mvc.perform(post("/api/meals").cookie(access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mealJson(slot, startedAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.mealId")).longValue();
    }

    private static String mealJson(String slot, Instant startedAt) {
        return "{\"mealSlot\":\"" + slot + "\",\"startedAt\":\"" + startedAt + "\"}";
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
    void 식사를_시작하면_201이고_오프라인은_기본_false() throws Exception {
        mvc.perform(post("/api/meals").cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content(mealJson("LUNCH", Instant.now())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.mealSlot").value("LUNCH"))
                .andExpect(jsonPath("$.data.startedOffline").value(false))
                .andExpect(jsonPath("$.data.note").doesNotExist());
    }

    @Test
    void 오프라인_기록과_메모를_같이_보낼_수_있다() throws Exception {
        mvc.perform(post("/api/meals").cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mealSlot\":\"DINNER\",\"startedAt\":\"" + Instant.now().minus(Duration.ofHours(1))
                                + "\",\"startedOffline\":true,\"note\":\"김치찌개\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.startedOffline").value(true))
                .andExpect(jsonPath("$.data.note").value("김치찌개"));
    }

    @Test
    void 끼니가_없거나_미래_시각이거나_메모가_길면_400() throws Exception {
        for (String body : List.of(
                "{\"startedAt\":\"" + Instant.now() + "\"}",
                mealJson("LUNCH", Instant.now().plus(Duration.ofDays(1))),
                "{\"mealSlot\":\"LUNCH\",\"startedAt\":\"" + Instant.now() + "\",\"note\":\"" + "가".repeat(256) + "\"}")) {
            mvc.perform(post("/api/meals").cookie(access)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        }
    }

    // ---------- 상세 ----------

    @Test
    void 내_식사는_조회되고_남의_식사나_없는_식사는_404() throws Exception {
        long id = create("BREAKFAST", Instant.now());
        mvc.perform(get("/api/meals/" + id).cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mealId").value(id));
        mvc.perform(get("/api/meals/" + id).cookie(other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40401"));
        mvc.perform(get("/api/meals/" + (id + 999)).cookie(access))
                .andExpect(status().isNotFound());
    }

    // ---------- 목록 ----------

    @Test
    void 목록은_적은_순서가_아니라_시작_시각_최신순() throws Exception {
        LocalDate day = today().minusDays(1);
        long breakfast = create("BREAKFAST", kst(day, 8, 0));
        long dinner = create("DINNER", kst(day, 19, 0));
        long lunch = create("LUNCH", kst(day, 12, 30));   // 늦게 적어서 id는 제일 크지만 시각은 중간

        mvc.perform(get("/api/meals").cookie(access)
                        .param("from", day.toString()).param("to", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].mealId").value(dinner))
                .andExpect(jsonPath("$.data.items[1].mealId").value(lunch))
                .andExpect(jsonPath("$.data.items[2].mealId").value(breakfast))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursorAt").doesNotExist());
    }

    @Test
    void 커서로_끝까지_넘기면_빠짐도_겹침도_없다() throws Exception {
        LocalDate day = today().minusDays(1);
        Instant sameTime = kst(day, 12, 0);
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.add(create("SNACK", sameTime));   // 시작 시각이 전부 같아도 id로 순서가 정해져야 함
        }

        List<Long> seen = new ArrayList<>();
        String cursorAt = null;
        String cursorId = null;
        boolean hasNext = true;
        while (hasNext) {
            var req = get("/api/meals").cookie(access)
                    .param("from", day.toString()).param("to", day.toString()).param("size", "2");
            if (cursorAt != null) req.param("cursorAt", cursorAt).param("cursorId", cursorId);
            String body = mvc.perform(req).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            List<Number> ids = JsonPath.read(body, "$.data.items[*].mealId");
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
        long lastMinute = create("SNACK", kst(day, 23, 59));
        create("BREAKFAST", kst(day.plusDays(1), 0, 0));   // 다음 날 0시는 빠져야 함 (UTC로는 같은 날 15시)

        mvc.perform(get("/api/meals").cookie(access)
                        .param("from", day.toString()).param("to", day.toString()))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].mealId").value(lastMinute));
    }

    @Test
    void 남의_식사는_목록에_안_나온다() throws Exception {
        create("LUNCH", Instant.now());
        mvc.perform(get("/api/meals").cookie(other)
                        .param("from", today().toString()).param("to", today().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void 기간이_뒤집히거나_커서를_하나만_보내면_400() throws Exception {
        mvc.perform(get("/api/meals").cookie(access)
                        .param("from", today().toString()).param("to", today().minusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40001"));
        mvc.perform(get("/api/meals").cookie(access)
                        .param("from", today().toString()).param("to", today().toString())
                        .param("cursorId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40002"));
    }

    // ---------- 인증 ----------

    @Test
    void 토큰_없으면_401() throws Exception {
        mvc.perform(get("/api/meals/1")).andExpect(status().isUnauthorized());
    }
}
