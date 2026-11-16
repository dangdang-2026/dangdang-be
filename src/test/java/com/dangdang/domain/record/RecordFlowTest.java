package com.dangdang.domain.record;

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
import java.time.YearMonth;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 오늘 화면 · 기록 통합 조회 · 기록 달력 흐름 테스트
@SpringBootTest
@AutoConfigureMockMvc
class RecordFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    Cookie access;   // 지수(주인공)
    Cookie other;    // 남의 기록이 안 섞이는지 확인용

    @BeforeEach
    void loginFirst() throws Exception {
        access = signupAndLogin("jisu@x.com");
        other = signupAndLogin("minho@x.com");
    }

    @AfterEach
    void clean() { // users를 지우면 readings·meals·criteria_versions도 같이 지워짐 (ON DELETE CASCADE)
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

    // 가입일을 며칠 앞으로 당김 (지난 날짜 기록이 "가입 전"으로 안 보이게)
    private void signedUpDaysAgo(String email, int days) {
        jdbc.update("UPDATE users SET created_at = ? WHERE email = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(days))), email);
    }

    private void createCriteria(Cookie who, int postMealMinutes, int fastingTarget, int postMealTarget) throws Exception {
        mvc.perform(post("/api/criteria-versions").cookie(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postMealMinutes\":" + postMealMinutes + ",\"fastingTarget\":" + fastingTarget
                                + ",\"postMealTarget\":" + postMealTarget + ",\"effectiveFrom\":\"" + today().minusDays(40) + "\"}"))
                .andExpect(status().isCreated());
    }

    private long createMeal(Cookie who, String slot, Instant startedAt) throws Exception {
        String body = mvc.perform(post("/api/meals").cookie(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mealSlot\":\"" + slot + "\",\"startedAt\":\"" + startedAt + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.mealId")).longValue();
    }

    private long createReading(Cookie who, int value, String type, Instant measuredAt, Long mealId) throws Exception {
        String meal = mealId == null ? "" : ",\"mealId\":" + mealId;
        String body = mvc.perform(post("/api/readings").cookie(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":" + value + ",\"measuredAt\":\"" + measuredAt
                                + "\",\"readingType\":\"" + type + "\"" + meal + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.readingId")).longValue();
    }

    private static Instant kst(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(ClockConfig.KST).toInstant();
    }

    private static LocalDate today() {
        return LocalDate.now(ClockConfig.KST);
    }

    // ---------- 오늘 화면 ----------

    @Test
    void 기록이_없으면_빈_목록과_null_칸이_그대로_온다() throws Exception {
        mvc.perform(get("/api/today").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value(today().toString()))
                .andExpect(jsonPath("$.data.criteria").value((Object) null))
                .andExpect(jsonPath("$.data.meals", hasSize(0)))
                .andExpect(jsonPath("$.data.readings", hasSize(0)))
                .andExpect(jsonPath("$.data.nextReminder").value((Object) null))
                .andExpect(jsonPath("$.data.pushReady").value(false));
    }

    @Test
    void 오늘_식사와_혈당만_오고_timing과_aboveTarget이_계산된다() throws Exception {
        createCriteria(access, 60, 95, 140);
        Instant now = Instant.now();
        long mealId = createMeal(access, "LUNCH", now.minus(Duration.ofMinutes(70)));
        createReading(access, 150, "POST_MEAL", now.minus(Duration.ofMinutes(5)), mealId);   // 예정(식사+60분)보다 5분 늦게 → 제때, 140 넘음
        createReading(access, 90, "FASTING", now.minus(Duration.ofMinutes(1)), null);        // 예정 시각 모름, 95 안 넘음
        createReading(access, 100, "FASTING", now.minus(Duration.ofDays(2)), null);          // 그저께 → 안 나옴
        createReading(other, 200, "FASTING", now.minus(Duration.ofMinutes(1)), null);        // 남의 기록 → 안 나옴

        mvc.perform(get("/api/today").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.criteria.postMealMinutes").value(60))
                .andExpect(jsonPath("$.data.meals", hasSize(1)))
                .andExpect(jsonPath("$.data.meals[0].mealId").value(mealId))
                .andExpect(jsonPath("$.data.readings", hasSize(2)))
                .andExpect(jsonPath("$.data.readings[0].value").value(150))
                .andExpect(jsonPath("$.data.readings[0].timing").value("ON_TIME"))
                .andExpect(jsonPath("$.data.readings[0].aboveTarget").value(true))
                .andExpect(jsonPath("$.data.readings[1].value").value(90))
                .andExpect(jsonPath("$.data.readings[1].timing").value((Object) null))
                .andExpect(jsonPath("$.data.readings[1].aboveTarget").value(false));
    }

    @Test
    void 예정보다_15분_넘게_늦으면_LATE_목표가_없으면_aboveTarget_null() throws Exception {
        createCriteria(access, 60, 95, 140);
        Instant now = Instant.now();
        long mealId = createMeal(access, "DINNER", now.minus(Duration.ofMinutes(90)));
        createReading(access, 120, "POST_MEAL", now.minus(Duration.ofMinutes(10)), mealId);   // 예정보다 20분 늦게
        createReading(access, 130, "OTHER", now.minus(Duration.ofMinutes(1)), null);           // 기타는 비교할 목표 없음

        mvc.perform(get("/api/today").cookie(access))
                .andExpect(jsonPath("$.data.readings[0].timing").value("LATE"))
                .andExpect(jsonPath("$.data.readings[1].aboveTarget").value((Object) null));
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mvc.perform(get("/api/today")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/records?from=2026-11-01&to=2026-11-01")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/records/calendar?month=2026-11")).andExpect(status().isUnauthorized());
    }

    // ---------- 기록 통합 조회 ----------

    @Test
    void 혈당과_식사가_날짜별로_시각순으로_섞여_나오고_기록_없는_날도_들어간다() throws Exception {
        LocalDate day = today().minusDays(2);
        long mealId = createMeal(access, "BREAKFAST", kst(day, 8, 0));
        long readingId = createReading(access, 108, "POST_MEAL", kst(day, 9, 0), mealId);
        long fastingId = createReading(access, 88, "FASTING", kst(day, 7, 0), null);
        createReading(other, 200, "FASTING", kst(day, 7, 30), null);   // 남의 기록 → 안 나옴

        mvc.perform(get("/api/records").cookie(access)
                        .param("from", day.toString()).param("to", day.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days", hasSize(2)))
                .andExpect(jsonPath("$.data.days[0].date").value(day.toString()))
                .andExpect(jsonPath("$.data.days[0].events", hasSize(3)))
                .andExpect(jsonPath("$.data.days[0].events[0].id").value(fastingId))
                .andExpect(jsonPath("$.data.days[0].events[0].type").value("READING"))
                .andExpect(jsonPath("$.data.days[0].events[0].meal").doesNotExist())
                .andExpect(jsonPath("$.data.days[0].events[1].type").value("MEAL"))
                .andExpect(jsonPath("$.data.days[0].events[1].id").value(mealId))
                .andExpect(jsonPath("$.data.days[0].events[1].meal.mealSlot").value("BREAKFAST"))
                .andExpect(jsonPath("$.data.days[0].events[1].meal.items", hasSize(0)))
                .andExpect(jsonPath("$.data.days[0].events[1].reading").doesNotExist())
                .andExpect(jsonPath("$.data.days[0].events[2].id").value(readingId))
                .andExpect(jsonPath("$.data.days[0].events[2].reading.mealId").value(mealId))
                .andExpect(jsonPath("$.data.days[0].events[2].modified").value(false))
                .andExpect(jsonPath("$.data.days[1].date").value(day.plusDays(1).toString()))
                .andExpect(jsonPath("$.data.days[1].events", hasSize(0)));
    }

    @Test
    void 한국_자정_직전_기록은_그날로_묶인다() throws Exception {
        LocalDate day = today().minusDays(3);
        createReading(access, 100, "OTHER", kst(day, 23, 59), null);

        mvc.perform(get("/api/records").cookie(access)
                        .param("from", day.toString()).param("to", day.plusDays(1).toString()))
                .andExpect(jsonPath("$.data.days[0].events", hasSize(1)))
                .andExpect(jsonPath("$.data.days[1].events", hasSize(0)));
    }

    @Test
    void 고친_기록은_modified_지운_기록은_안_나온다() throws Exception {
        LocalDate day = today().minusDays(1);
        long edited = createReading(access, 100, "FASTING", kst(day, 7, 0), null);
        long deleted = createReading(access, 110, "FASTING", kst(day, 8, 0), null);
        mvc.perform(patch("/api/readings/" + edited).cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":101}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/readings/" + deleted).cookie(access)).andExpect(status().isOk());

        mvc.perform(get("/api/records").cookie(access)
                        .param("from", day.toString()).param("to", day.toString()))
                .andExpect(jsonPath("$.data.days[0].events", hasSize(1)))
                .andExpect(jsonPath("$.data.days[0].events[0].id").value(edited))
                .andExpect(jsonPath("$.data.days[0].events[0].modified").value(true));
    }

    @Test
    void 날짜가_없거나_틀리거나_거꾸로거나_31일을_넘으면_400() throws Exception {
        LocalDate day = today();
        String[][] cases = {
                {null, day.toString()},
                {"2026/11/01", day.toString()},
                {day.toString(), day.minusDays(1).toString()},
                {day.minusDays(31).toString(), day.toString()},
        };
        for (String[] c : cases) {
            var req = get("/api/records").cookie(access).param("to", c[1]);
            if (c[0] != null) req = req.param("from", c[0]);
            mvc.perform(req)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        }
        // 딱 31일(30일 차이)까지는 됨
        mvc.perform(get("/api/records").cookie(access)
                        .param("from", day.minusDays(30).toString()).param("to", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days", hasSize(31)));
    }

    // ---------- 기록 달력 ----------

    @Test
    void 이번_달_달력은_오늘_미래_기록_있음_없음을_구분한다() throws Exception {
        signedUpDaysAgo("jisu@x.com", 40);
        YearMonth month = YearMonth.from(today());
        LocalDate recordedDay = today().minusDays(1);
        boolean hasPastDayInMonth = YearMonth.from(recordedDay).equals(month);   // 1일이면 이번 달 지난 날이 없음
        if (hasPastDayInMonth) {
            createMeal(access, "LUNCH", kst(recordedDay, 12, 0));
        }

        var result = mvc.perform(get("/api/records/calendar").cookie(access).param("month", month.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value(month.toString()))
                .andExpect(jsonPath("$.data.days", hasSize(month.lengthOfMonth())))
                .andExpect(jsonPath("$.data.hasPrevMonth").value(true))
                .andExpect(jsonPath("$.data.hasNextMonth").value(false))
                .andExpect(jsonPath("$.data.days[" + (today().getDayOfMonth() - 1) + "].status").value("TODAY"))
                .andExpect(jsonPath("$.data.days[0].visit").value(false));

        if (hasPastDayInMonth) {
            int idx = recordedDay.getDayOfMonth() - 1;
            result.andExpect(jsonPath("$.data.days[" + idx + "].status").value("RECORDED"))
                    .andExpect(jsonPath("$.data.firstRecordDate").value(recordedDay.toString()));
        }
        if (today().getDayOfMonth() > 2) {   // 이번 달 1일은 기록 없는 날
            result.andExpect(jsonPath("$.data.days[0].status").value("EMPTY"));
        }
        if (today().getDayOfMonth() < month.lengthOfMonth()) {
            result.andExpect(jsonPath("$.data.days[" + (month.lengthOfMonth() - 1) + "].status").value("FUTURE"));
        }
    }

    @Test
    void 가입하기_전_달은_전부_BEFORE_SIGNUP이고_이전_달_없음() throws Exception {
        YearMonth lastMonth = YearMonth.from(today()).minusMonths(1);   // 방금 가입한 사람의 지난달

        mvc.perform(get("/api/records/calendar").cookie(access).param("month", lastMonth.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].status").value("BEFORE_SIGNUP"))
                .andExpect(jsonPath("$.data.days[" + (lastMonth.lengthOfMonth() - 1) + "].status").value("BEFORE_SIGNUP"))
                .andExpect(jsonPath("$.data.hasPrevMonth").value(false))
                .andExpect(jsonPath("$.data.hasNextMonth").value(true))
                .andExpect(jsonPath("$.data.firstRecordDate").value((Object) null));
    }

    @Test
    void month가_없거나_형식이_틀리면_400() throws Exception {
        for (String month : new String[]{null, "2026-13", "2026-1", "2026-11-01"}) {
            var req = get("/api/records/calendar").cookie(access);
            if (month != null) req = req.param("month", month);
            mvc.perform(req)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        }
    }
}
