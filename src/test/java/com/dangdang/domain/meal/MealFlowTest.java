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

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    @Test
    void 파라미터가_없거나_형식이_틀리면_500이_아니라_400() throws Exception {
        mvc.perform(get("/api/meals").cookie(access).param("to", today().toString()))   // from 없음
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        mvc.perform(get("/api/meals").cookie(access).param("from", "abc").param("to", today().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        mvc.perform(get("/api/meals/abc").cookie(access))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"))
                .andExpect(jsonPath("$.message").value("mealId 값의 형식이 맞지 않아요."));   // 서버 내부 문구 안 나감
    }

    // ---------- 수정 ----------

    @Test
    void 보낸_칸만_바뀌고_나머지는_그대로() throws Exception {
        long id = create("LUNCH", Instant.now());
        mvc.perform(patch("/api/meals/" + id).cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"비빔밥\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.note").value("비빔밥"))
                .andExpect(jsonPath("$.data.mealSlot").value("LUNCH"));
        mvc.perform(patch("/api/meals/" + id).cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mealSlot\":\"DINNER\"}"))
                .andExpect(jsonPath("$.data.mealSlot").value("DINNER"))
                .andExpect(jsonPath("$.data.note").value("비빔밥"));
    }

    @Test
    void 바꿀_값이_없거나_미래_시각이면_400() throws Exception {
        long id = create("LUNCH", Instant.now());
        mvc.perform(patch("/api/meals/" + id).cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40003"));
        mvc.perform(patch("/api/meals/" + id).cookie(access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startedAt\":\"" + Instant.now().plus(Duration.ofDays(1)) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
    }

    @Test
    void 남의_식사는_수정할_수_없다() throws Exception {
        long id = create("LUNCH", Instant.now());
        mvc.perform(patch("/api/meals/" + id).cookie(other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40401"));
    }

    // ---------- 삭제 ----------

    @Test
    void 삭제하면_상세와_목록에서_사라지고_다시_지우면_404() throws Exception {
        long id = create("LUNCH", Instant.now());
        mvc.perform(delete("/api/meals/" + id).cookie(access))
                .andExpect(status().isOk());

        mvc.perform(get("/api/meals/" + id).cookie(access))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/meals").cookie(access)
                        .param("from", today().toString()).param("to", today().toString()))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        mvc.perform(delete("/api/meals/" + id).cookie(access))
                .andExpect(status().isNotFound());
    }

    @Test
    void 남의_식사는_삭제할_수_없다() throws Exception {
        long id = create("LUNCH", Instant.now());
        mvc.perform(delete("/api/meals/" + id).cookie(other))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/meals/" + id).cookie(access))
                .andExpect(status().isOk());   // 주인 쪽에서는 그대로 있음
    }

    // ---------- 식후 측정 알림 연결 ----------

    // 병원 기준: 식후 60분, 점심 식후만 잼
    private void saveLunchCriteria() throws Exception {
        mvc.perform(post("/api/criteria-versions").cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postMealMinutes\":60,\"measureSlots\":[\"LUNCH_AFTER\"]}"))
                .andExpect(status().isCreated());
    }

    private List<Map<String, Object>> remindersOf(long mealId) {
        return jdbc.queryForList(
                "SELECT status, slot, scheduled_at FROM reminders WHERE meal_id = ? ORDER BY id", mealId);
    }

    private static Instant at(Map<String, Object> row) {
        return ((Timestamp) row.get("scheduled_at")).toInstant();
    }

    @Test
    void 점심을_등록하면_식후_60분_뒤로_알림이_예약된다() throws Exception {
        saveLunchCriteria();
        Instant startedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long id = create("LUNCH", startedAt);

        List<Map<String, Object>> rows = remindersOf(id);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("status")).isEqualTo("SCHEDULED");
        assertThat(rows.get(0).get("slot")).isEqualTo("LUNCH_AFTER");
        assertThat(at(rows.get(0))).isEqualTo(startedAt.plus(Duration.ofMinutes(60)));
    }

    @Test
    void 병원에서_안_재라고_한_끼니나_기준이_없으면_알림_없음() throws Exception {
        long noCriteria = create("LUNCH", Instant.now());   // 기준 등록 전
        saveLunchCriteria();
        long dinner = create("DINNER", Instant.now());      // 저녁 식후는 안 잼
        long snack = create("SNACK", Instant.now());

        assertThat(remindersOf(noCriteria)).isEmpty();
        assertThat(remindersOf(dinner)).isEmpty();
        assertThat(remindersOf(snack)).isEmpty();
    }

    @Test
    void 시작_시각을_바꾸면_예전_알림은_취소되고_새로_예약된다() throws Exception {
        saveLunchCriteria();
        long id = create("LUNCH", Instant.now());
        Instant newStart = Instant.now().minus(Duration.ofMinutes(20)).truncatedTo(ChronoUnit.SECONDS);

        mvc.perform(patch("/api/meals/" + id).cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startedAt\":\"" + newStart + "\"}"))
                .andExpect(status().isOk());

        List<Map<String, Object>> rows = remindersOf(id);
        assertThat(rows).extracting(r -> r.get("status")).containsExactly("CANCELED", "SCHEDULED");
        assertThat(at(rows.get(1))).isEqualTo(newStart.plus(Duration.ofMinutes(60)));
    }

    @Test
    void 지난_식사를_적거나_시각을_과거로_고치면_알림을_안_만든다() throws Exception {
        saveLunchCriteria();
        long late = create("LUNCH", Instant.now().minus(Duration.ofHours(3)));   // 식후 60분이 이미 지남
        assertThat(remindersOf(late)).isEmpty();

        long id = create("LUNCH", Instant.now());
        mvc.perform(patch("/api/meals/" + id).cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startedAt\":\"" + Instant.now().minus(Duration.ofHours(3)) + "\"}"))
                .andExpect(status().isOk());
        assertThat(remindersOf(id)).extracting(r -> r.get("status")).containsExactly("CANCELED");   // 예전 것만 취소, 새로 안 만듦
    }

    @Test
    void 끼니를_안_재는_끼니로_바꾸면_알림만_취소된다() throws Exception {
        saveLunchCriteria();
        long id = create("LUNCH", Instant.now());

        mvc.perform(patch("/api/meals/" + id).cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mealSlot\":\"SNACK\"}"))
                .andExpect(status().isOk());

        assertThat(remindersOf(id)).extracting(r -> r.get("status")).containsExactly("CANCELED");
    }

    @Test
    void 메모만_바꾸면_알림은_그대로() throws Exception {
        saveLunchCriteria();
        long id = create("LUNCH", Instant.now());

        mvc.perform(patch("/api/meals/" + id).cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"비빔밥\"}"))
                .andExpect(status().isOk());

        assertThat(remindersOf(id)).extracting(r -> r.get("status")).containsExactly("SCHEDULED");
    }

    @Test
    void 이미_보낸_알림이_있으면_시각을_바꿔도_다시_예약하지_않는다() throws Exception {
        saveLunchCriteria();
        long id = create("LUNCH", Instant.now());
        jdbc.update("UPDATE reminders SET status = 'SENT' WHERE meal_id = ?", id);   // 스케줄러가 보냈다고 침

        mvc.perform(patch("/api/meals/" + id).cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startedAt\":\"" + Instant.now().minus(Duration.ofMinutes(10)) + "\"}"))
                .andExpect(status().isOk());

        assertThat(remindersOf(id)).extracting(r -> r.get("status")).containsExactly("SENT");
    }

    @Test
    void 식사를_지우면_예약된_알림이_취소된다() throws Exception {
        saveLunchCriteria();
        long id = create("LUNCH", Instant.now());

        mvc.perform(delete("/api/meals/" + id).cookie(access)).andExpect(status().isOk());

        assertThat(remindersOf(id)).extracting(r -> r.get("status")).containsExactly("CANCELED");
    }

    // ---------- 인증 ----------

    @Test
    void 토큰_없으면_401() throws Exception {
        mvc.perform(get("/api/meals/1")).andExpect(status().isUnauthorized());
    }
}
