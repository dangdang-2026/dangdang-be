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

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static com.dangdang.support.TestConsents.grantSensitive;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 혈당 통계 흐름 테스트. 기록은 전부 어제 이전 날짜의 낮 시각으로 만들어서 테스트를 언제 돌려도 같은 결과가 나옴
@SpringBootTest
@AutoConfigureMockMvc
class ReadingStatsFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    Cookie access;
    Cookie other;
    Long jisuId;

    @BeforeEach
    void loginFirst() throws Exception {
        access = signupAndLogin("jisu@x.com");
        other = signupAndLogin("minho@x.com");
        jisuId = userRepository.findAll().stream().filter(u -> "jisu@x.com".equals(u.getEmail())).findFirst().orElseThrow().getId();
    }

    @AfterEach
    void clean() { // users를 지우면 readings·meals·reminders·criteria_versions도 같이 지워짐 (ON DELETE CASCADE)
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private Cookie signupAndLogin(String email) throws Exception {
        grantSensitive(jdbc,
                userRepository.save(User.ofEmail(email, "테스트", passwordEncoder.encode("abcd1234"), Instant.now())));
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    private void criteria(Cookie who, int fastingTarget, int postMealTarget, LocalDate effectiveFrom) throws Exception {
        mvc.perform(post("/api/criteria-versions").cookie(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fastingTarget\":" + fastingTarget + ",\"postMealTarget\":" + postMealTarget
                                + ",\"effectiveFrom\":\"" + effectiveFrom + "\"}"))
                .andExpect(status().isCreated());
    }

    private long meal(Cookie who, String slot, LocalDate day, int hour) throws Exception {
        String body = mvc.perform(post("/api/meals").cookie(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mealSlot\":\"" + slot + "\",\"startedAt\":\"" + kst(day, hour) + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.mealId")).longValue();
    }

    // 혈당 저장. mealId·slot은 없으면 null
    private void reading(Cookie who, int value, String type, LocalDate day, int hour, Long mealId, String slot) throws Exception {
        String extra = (mealId == null ? "" : ",\"mealId\":" + mealId) + (slot == null ? "" : ",\"slot\":\"" + slot + "\"");
        mvc.perform(post("/api/readings").cookie(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":" + value + ",\"measuredAt\":\"" + kst(day, hour)
                                + "\",\"readingType\":\"" + type + "\"" + extra + "}"))
                .andExpect(status().isCreated());
    }

    // 정확한 시각으로 혈당 저장하고 번호를 돌려줌 (분 단위 경계 확인용)
    private long readingAt(Cookie who, int value, String type, Instant measuredAt) throws Exception {
        String body = mvc.perform(post("/api/readings").cookie(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":" + value + ",\"measuredAt\":\"" + measuredAt
                                + "\",\"readingType\":\"" + type + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.readingId")).longValue();
    }

    // 알림 하나를 바로 DB에 넣음 (보냈는데 안 잰 알림 = SENT)
    private void reminder(Long userId, String slot, LocalDate day, int hour, String status) {
        reminderAt(userId, slot, kst(day, hour), status);
    }

    private void reminderAt(Long userId, String slot, Instant scheduledAt, String status) {
        jdbc.update("INSERT INTO reminders (user_id, slot, scheduled_at, status) VALUES (?, ?, ?, ?)",
                userId, slot, Timestamp.from(scheduledAt), status);
    }

    private static Instant kst(LocalDate date, int hour) {
        return date.atTime(hour, 0).atZone(ClockConfig.KST).toInstant();
    }

    private static LocalDate today() {
        return LocalDate.now(ClockConfig.KST);
    }

    private static LocalDate daysAgo(int n) {
        return today().minusDays(n);
    }

    // ---------- 정상 ----------

    @Test
    void 분모와_70_미만과_잰_날_수와_처음_기록일이_온다() throws Exception {
        criteria(access, 95, 140, daysAgo(200));
        reading(access, 88, "FASTING", daysAgo(3), 7, null, null);
        reading(access, 97, "FASTING", daysAgo(2), 7, null, null);   // 95 넘음
        reading(access, 68, "FASTING", daysAgo(1), 7, null, null);   // 70 미만 (목표 안)

        mvc.perform(get("/api/readings/stats").cookie(access).param("slot", "FASTING").param("period", "1W"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current.count").value(3))
                .andExpect(jsonPath("$.data.current.judged").value(3))
                .andExpect(jsonPath("$.data.current.inTarget").value(2))
                .andExpect(jsonPath("$.data.current.judgedDays").value(3))
                .andExpect(jsonPath("$.data.current.inTargetDays").value(2))
                .andExpect(jsonPath("$.data.current.overDays").value(1))
                .andExpect(jsonPath("$.data.current.belowLow").value(1))
                .andExpect(jsonPath("$.data.firstRecordDate").value(daysAgo(3).toString()));
    }

    @Test
    void 기록이_없으면_처음_기록일은_null이고_분모는_0() throws Exception {
        mvc.perform(get("/api/readings/stats").cookie(access).param("slot", "FASTING").param("period", "1W"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstRecordDate").value((Object) null))
                .andExpect(jsonPath("$.data.current.judged").value(0))
                .andExpect(jsonPath("$.data.current.inTargetRate").value((Object) null))
                .andExpect(jsonPath("$.data.current.belowLow").value(0));
    }

    @Test
    void 식후_1주_통계와_지난주_비교() throws Exception {
        criteria(access, 95, 140, daysAgo(200));
        long lunch = meal(access, "LUNCH", daysAgo(3), 12);
        reading(access, 120, "POST_MEAL", daysAgo(3), 13, lunch, null);        // 점심, 목표 안
        reading(access, 150, "POST_MEAL", daysAgo(2), 19, null, "DINNER_AFTER"); // 저녁(잰 때로 끼니), 목표 넘음
        reading(access, 130, "POST_MEAL", daysAgo(2), 13, null, "LUNCH_AFTER");  // 점심, 목표 안
        reading(access, 160, "POST_MEAL", daysAgo(10), 13, null, "LUNCH_AFTER"); // 지난주 점심
        reading(access, 90, "FASTING", daysAgo(2), 7, null, null);               // 공복은 식후 통계에 안 섞임
        reading(other, 200, "POST_MEAL", daysAgo(2), 13, null, null);            // 남의 기록

        mvc.perform(get("/api/readings/stats").cookie(access).param("slot", "POST_MEAL").param("period", "1W"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slot").value("POST_MEAL"))
                .andExpect(jsonPath("$.data.range.from").value(daysAgo(6).toString()))
                .andExpect(jsonPath("$.data.range.to").value(today().toString()))
                .andExpect(jsonPath("$.data.compareRange.from").value(daysAgo(13).toString()))
                .andExpect(jsonPath("$.data.compareRange.to").value(daysAgo(7).toString()))
                .andExpect(jsonPath("$.data.targetValue").value(140))
                .andExpect(jsonPath("$.data.current.count").value(3))
                .andExpect(jsonPath("$.data.current.average").value(133))      // (120+150+130)/3 = 133.3
                .andExpect(jsonPath("$.data.current.inTargetRate").value(0.67)) // 3개 중 2개
                .andExpect(jsonPath("$.data.current.overDays").value(1))
                .andExpect(jsonPath("$.data.previous.count").value(1))
                .andExpect(jsonPath("$.data.previous.average").value(160))
                .andExpect(jsonPath("$.data.previous.inTargetRate").value(0.0))
                .andExpect(jsonPath("$.data.series", hasSize(7)))
                .andExpect(jsonPath("$.data.series[4].date").value(daysAgo(2).toString()))
                .andExpect(jsonPath("$.data.series[4].average").value(140))      // (150+130)/2
                .andExpect(jsonPath("$.data.series[4].count").value(2))
                .andExpect(jsonPath("$.data.series[0].average").value((Object) null))
                .andExpect(jsonPath("$.data.series[0].count").value(0))
                .andExpect(jsonPath("$.data.previousSeries", hasSize(7)))
                .andExpect(jsonPath("$.data.byMeal", hasSize(2)))
                .andExpect(jsonPath("$.data.byMeal[0].meal").value("LUNCH"))
                .andExpect(jsonPath("$.data.byMeal[0].average").value(125))      // (120+130)/2
                .andExpect(jsonPath("$.data.byMeal[0].previousAverage").value(160))
                .andExpect(jsonPath("$.data.byMeal[1].meal").value("DINNER"))
                .andExpect(jsonPath("$.data.byMeal[1].average").value(150))
                .andExpect(jsonPath("$.data.byMeal[1].previousAverage").value((Object) null));
    }

    @Test
    void 공복_통계는_공복만_세고_끼니별은_빈_배열() throws Exception {
        criteria(access, 95, 140, daysAgo(200));
        reading(access, 90, "FASTING", daysAgo(1), 7, null, null);
        reading(access, 100, "FASTING", daysAgo(2), 7, null, null);
        reading(access, 150, "POST_MEAL", daysAgo(1), 13, null, null);

        mvc.perform(get("/api/readings/stats").cookie(access).param("slot", "FASTING").param("period", "1W"))
                .andExpect(jsonPath("$.data.targetValue").value(95))
                .andExpect(jsonPath("$.data.current.count").value(2))
                .andExpect(jsonPath("$.data.current.average").value(95))
                .andExpect(jsonPath("$.data.current.inTargetRate").value(0.5))   // 100은 95 이상이라 넘음
                .andExpect(jsonPath("$.data.byMeal", hasSize(0)));
    }

    @Test
    void 기준이_바뀌면_그날부터_새_기준으로_목표_안을_센다() throws Exception {
        criteria(access, 95, 140, daysAgo(200));
        criteria(access, 95, 120, daysAgo(3));                                 // 3일 전부터 120으로 엄격해짐
        reading(access, 130, "POST_MEAL", daysAgo(5), 13, null, null);         // 그땐 140 기준 → 안
        reading(access, 130, "POST_MEAL", daysAgo(2), 13, null, null);         // 지금은 120 기준 → 넘음

        mvc.perform(get("/api/readings/stats").cookie(access).param("period", "1W"))
                .andExpect(jsonPath("$.data.targetValue").value(120))           // 지금 목표
                .andExpect(jsonPath("$.data.current.inTargetRate").value(0.5))
                .andExpect(jsonPath("$.data.current.overDays").value(1));
    }

    @Test
    void 기본값은_식후_1개월() throws Exception {
        mvc.perform(get("/api/readings/stats").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slot").value("POST_MEAL"))
                .andExpect(jsonPath("$.data.range.from").value(daysAgo(29).toString()))
                .andExpect(jsonPath("$.data.series", hasSize(30)));
    }

    @Test
    void 놓친_측정은_보냈거나_건너뛰었는데_안_잰_알림만_센다() throws Exception {
        reminder(jisuId, "LUNCH_AFTER", daysAgo(2), 13, "SENT");     // 놓침
        reminder(jisuId, "DINNER_AFTER", daysAgo(2), 19, "OPENED");  // 열어만 봄 → 놓침
        reminder(jisuId, "DINNER_AFTER", daysAgo(1), 19, "DISMISSED"); // 「이번 끼니는 건너뛸게요」 → 놓침 (H-05)
        reminder(jisuId, "BREAKFAST_AFTER", daysAgo(1), 9, "DONE");  // 잼 → 안 셈
        reminder(jisuId, "LUNCH_AFTER", daysAgo(1), 13, "SKIPPED");  // 안 보냄 → 안 셈
        reminder(jisuId, "FASTING", daysAgo(1), 7, "SENT");          // 공복 알림 → 식후 통계엔 안 셈
        reminder(jisuId, "LUNCH_AFTER", daysAgo(10), 13, "SENT");    // 지난주 → 이번 주엔 안 셈

        mvc.perform(get("/api/readings/stats").cookie(access).param("period", "1W"))
                .andExpect(jsonPath("$.data.current.missed").value(3));
        mvc.perform(get("/api/readings/stats").cookie(access).param("slot", "FASTING").param("period", "1W"))
                .andExpect(jsonPath("$.data.current.missed").value(1));
    }

    // ---------- 기록이 없을 때 ----------

    @Test
    void 기록이_하나도_없으면_평균_비율은_null이고_비교도_null() throws Exception {
        mvc.perform(get("/api/readings/stats").cookie(access).param("period", "1W"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetValue").value((Object) null))
                .andExpect(jsonPath("$.data.current.count").value(0))
                .andExpect(jsonPath("$.data.current.average").value((Object) null))
                .andExpect(jsonPath("$.data.current.inTargetRate").value((Object) null))
                .andExpect(jsonPath("$.data.previous").value((Object) null))
                .andExpect(jsonPath("$.data.previousSeries").value((Object) null))
                .andExpect(jsonPath("$.data.byMeal", hasSize(0)));
    }

    @Test
    void 목표가_없던_기록은_비율에서_빠진다() throws Exception {
        reading(access, 130, "POST_MEAL", daysAgo(2), 13, null, null);   // 기준 없이 저장 → 빈 기준

        mvc.perform(get("/api/readings/stats").cookie(access).param("period", "1W"))
                .andExpect(jsonPath("$.data.current.count").value(1))
                .andExpect(jsonPath("$.data.current.average").value(130))
                .andExpect(jsonPath("$.data.current.inTargetRate").value((Object) null))
                .andExpect(jsonPath("$.data.current.overDays").value(0));
    }

    // ---------- 기간 직접 고르기 ----------

    @Test
    void 기간을_직접_고르면_그_길이만큼_앞_기간과_비교한다() throws Exception {
        LocalDate from = daysAgo(9);
        LocalDate to = daysAgo(5);   // 5일
        mvc.perform(get("/api/readings/stats").cookie(access).param("period", "CUSTOM")
                        .param("from", from.toString()).param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.range.from").value(from.toString()))
                .andExpect(jsonPath("$.data.range.to").value(to.toString()))
                .andExpect(jsonPath("$.data.compareRange.from").value(daysAgo(14).toString()))
                .andExpect(jsonPath("$.data.compareRange.to").value(daysAgo(10).toString()))
                .andExpect(jsonPath("$.data.series", hasSize(5)));
    }

    @Test
    void 정해진_기간이면_from_to는_무시한다() throws Exception {
        mvc.perform(get("/api/readings/stats").cookie(access).param("period", "2W")
                        .param("from", daysAgo(100).toString()).param("to", daysAgo(90).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.range.from").value(daysAgo(13).toString()))
                .andExpect(jsonPath("$.data.series", hasSize(14)));
    }

    // ---------- 잘못된 요청 ----------

    @Test
    void 잘못된_값이면_400() throws Exception {
        String[][] cases = {
                {"slot", "OTHER"},                     // 기타는 통계 없음
                {"slot", "fasting"},                   // 대소문자 틀림
                {"period", "5D"},                      // 없는 기간
                {"period", "CUSTOM"},                  // from·to 없음
        };
        for (String[] c : cases) {
            mvc.perform(get("/api/readings/stats").cookie(access).param(c[0], c[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        }
        String[][] customs = {
                {daysAgo(1).toString(), today().plusDays(1).toString()},        // 미래까지
                {daysAgo(300).toString(), today().toString()},                  // 301일
                {"2026/11/01", today().toString()},                             // 형식
                {daysAgo(3).toString(), null},                                  // to 없음
        };
        for (String[] c : customs) {
            var req = get("/api/readings/stats").cookie(access).param("period", "CUSTOM").param("from", c[0]);
            if (c[1] != null) req = req.param("to", c[1]);
            mvc.perform(req)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        }
        // 딱 300일까지는 됨
        mvc.perform(get("/api/readings/stats").cookie(access).param("period", "CUSTOM")
                        .param("from", daysAgo(299).toString()).param("to", today().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void from이_to보다_늦으면_목록_API와_같은_READING_40001() throws Exception {
        mvc.perform(get("/api/readings/stats").cookie(access).param("period", "CUSTOM")
                        .param("from", daysAgo(1).toString()).param("to", daysAgo(5).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("READING_40001"));
    }

    // ---------- 리뷰 반영 ----------

    @Test
    void 방금_보낸_알림은_아직_놓친_측정으로_안_센다() throws Exception {
        Instant now = Instant.now();
        reminderAt(jisuId, "LUNCH_AFTER", now.minus(Duration.ofMinutes(1)), "SENT");    // 1분 전 → 아직 잴 시간 있음
        reminderAt(jisuId, "DINNER_AFTER", now.minus(Duration.ofMinutes(40)), "SENT");  // 40분 전 → 놓침

        mvc.perform(get("/api/readings/stats").cookie(access).param("period", "1W"))
                .andExpect(jsonPath("$.data.current.missed").value(1));
    }

    @Test
    void 지난_기간에_기록이_없어도_놓친_측정이_있으면_비교한다() throws Exception {
        reading(access, 130, "POST_MEAL", daysAgo(2), 13, null, null);         // 이번 주 기록 1개
        for (int d = 7; d <= 13; d++) {
            reminder(jisuId, "LUNCH_AFTER", daysAgo(d), 13, "SENT");           // 지난주 7번 다 놓침
        }

        mvc.perform(get("/api/readings/stats").cookie(access).param("period", "1W"))
                .andExpect(jsonPath("$.data.current.missed").value(0))
                .andExpect(jsonPath("$.data.previous.count").value(0))
                .andExpect(jsonPath("$.data.previous.average").value((Object) null))
                .andExpect(jsonPath("$.data.previous.missed").value(7))
                .andExpect(jsonPath("$.data.previousSeries", hasSize(7)));
    }

    @Test
    void 한국_새벽_0시30분_기록은_그_한국_날짜로_들어간다() throws Exception {
        // 한국 00:30은 UTC로 전날 15:30. UTC로 자르면 하루 앞 날짜로 잘못 들어감
        readingAt(access, 150, "POST_MEAL", kst(daysAgo(6), 0).plus(Duration.ofMinutes(30)));  // 이번 기간 첫날
        readingAt(access, 110, "POST_MEAL", kst(daysAgo(7), 0).plus(Duration.ofMinutes(30)));  // 비교 기간 마지막 날

        mvc.perform(get("/api/readings/stats").cookie(access).param("period", "1W"))
                .andExpect(jsonPath("$.data.current.count").value(1))
                .andExpect(jsonPath("$.data.series[0].date").value(daysAgo(6).toString()))
                .andExpect(jsonPath("$.data.series[0].average").value(150))
                .andExpect(jsonPath("$.data.previous.count").value(1))
                .andExpect(jsonPath("$.data.previousSeries[6].date").value(daysAgo(7).toString()))
                .andExpect(jsonPath("$.data.previousSeries[6].average").value(110));
    }

    @Test
    void 지운_기록은_통계에서_빠진다() throws Exception {
        reading(access, 120, "POST_MEAL", daysAgo(2), 13, null, null);
        long deleted = readingAt(access, 300, "POST_MEAL", kst(daysAgo(1), 13));
        mvc.perform(delete("/api/readings/" + deleted).cookie(access)).andExpect(status().isOk());

        mvc.perform(get("/api/readings/stats").cookie(access).param("period", "1W"))
                .andExpect(jsonPath("$.data.current.count").value(1))
                .andExpect(jsonPath("$.data.current.average").value(120));
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mvc.perform(get("/api/readings/stats")).andExpect(status().isUnauthorized());
    }
}
