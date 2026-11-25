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
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

import static com.dangdang.support.TestConsents.grantSensitive;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
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
        grantSensitive(jdbc,
                userRepository.save(User.ofEmail(email, "테스트", passwordEncoder.encode("abcd1234"), Instant.now())));
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

    // "지금 − n분"으로 오늘 기록을 만드는 테스트는 한국 새벽에 돌면 그 기록이 어제로 넘어가서 실패함
    // → 새벽 4시 전이면 건너뜀 (실패가 아니라 skip으로 남음)
    private static void assumeAwayFromMidnight() {
        assumeTrue(LocalTime.now(ClockConfig.KST).isAfter(LocalTime.of(4, 0)),
                "한국 새벽(00~04시)엔 '오늘' 기록이 어제로 넘어가서 건너뜀");
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
                .andExpect(jsonPath("$.data.insulinLogs", hasSize(0)))
                .andExpect(jsonPath("$.data.nextReminder").value((Object) null))
                .andExpect(jsonPath("$.data.pushReady").value(false));
    }

    @Test
    void 오늘_인슐린_기록만_맞은_시각_순으로_온다() throws Exception {
        // 오늘 0시 1분·2분과 어젯밤으로 만듦. 0시 3분 전에 돌면 0시 2분이 아직 미래라 등록이 400 → 건너뜀
        assumeTrue(LocalTime.now(ClockConfig.KST).isAfter(LocalTime.of(0, 3)), "오늘 00:02 기록을 만들려면 00:03 이후여야 함");
        long first = createInsulin(access, "MEAL", "4", kst(today(), 0, 1));
        long second = createInsulin(access, "BEDTIME", "10.5", kst(today(), 0, 2));
        createInsulin(access, "MEAL", "6", kst(today().minusDays(1), 23, 59));     // 어젯밤 → 안 나옴
        long deleted = createInsulin(access, "MEAL", "3", kst(today(), 0, 1));
        jdbc.update("UPDATE insulin_logs SET deleted_at = now() WHERE id = ?", deleted);   // 지운 기록 → 안 나옴
        createInsulin(other, "MEAL", "8", kst(today(), 0, 1));                      // 남의 기록 → 안 나옴

        mvc.perform(get("/api/today").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.insulinLogs", hasSize(2)))
                .andExpect(jsonPath("$.data.insulinLogs[0].insulinLogId").value(first))
                .andExpect(jsonPath("$.data.insulinLogs[0].insulinType").value("MEAL"))
                .andExpect(jsonPath("$.data.insulinLogs[0].units").value(4.0))
                .andExpect(jsonPath("$.data.insulinLogs[1].insulinLogId").value(second))
                .andExpect(jsonPath("$.data.insulinLogs[1].units").value(10.5));
    }

    // 식사 때(MEAL)는 끼니가 필수라 아침 식사로 채움
    private long createInsulin(Cookie who, String type, String units, Instant takenAt) throws Exception {
        String slot = "MEAL".equals(type) ? ",\"mealSlot\":\"BREAKFAST\"" : "";
        String body = mvc.perform(post("/api/insulin-logs").cookie(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"insulinType\":\"" + type + "\"" + slot + ",\"units\":" + units
                                + ",\"takenAt\":\"" + takenAt + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.insulinLogId")).longValue();
    }

    @Test
    void 오늘_식사와_혈당만_오고_timing과_aboveTarget이_계산된다() throws Exception {
        assumeAwayFromMidnight();
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
        assumeAwayFromMidnight();
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
    void 지운_식사에_붙은_혈당이나_공복_혈당은_timing이_null() throws Exception {
        assumeAwayFromMidnight();
        createCriteria(access, 60, 95, 140);
        Instant now = Instant.now();
        long deletedMeal = createMeal(access, "LUNCH", now.minus(Duration.ofMinutes(70)));
        createReading(access, 150, "POST_MEAL", now.minus(Duration.ofMinutes(5)), deletedMeal);
        mvc.perform(delete("/api/meals/" + deletedMeal).cookie(access)).andExpect(status().isOk());
        long liveMeal = createMeal(access, "DINNER", now.minus(Duration.ofMinutes(200)));
        createReading(access, 90, "FASTING", now.minus(Duration.ofMinutes(1)), liveMeal);   // 공복인데 식사가 붙음

        mvc.perform(get("/api/today").cookie(access))
                .andExpect(jsonPath("$.data.readings", hasSize(2)))
                .andExpect(jsonPath("$.data.readings[0].timing").value((Object) null))
                .andExpect(jsonPath("$.data.readings[1].timing").value((Object) null));
    }

    @Test
    void 알림을_받은_식후_혈당은_알림_예정_시각과_비교한다() throws Exception {
        assumeAwayFromMidnight();
        // 식사 땐 식후 60분 기준 → 알림이 식사 + 60분(= 지금 + 10분)에 예약됨
        mvc.perform(post("/api/criteria-versions").cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postMealMinutes\":60,\"measureSlots\":[\"LUNCH_AFTER\"],\"postMealTarget\":140,"
                                + "\"effectiveFrom\":\"" + today().minusDays(40) + "\"}"))
                .andExpect(status().isCreated());
        Instant now = Instant.now();
        long mealId = createMeal(access, "LUNCH", now.minus(Duration.ofMinutes(50)));
        // 그 뒤 오늘부터 식후 30분으로 바뀜 → 기준으로만 계산하면 식사 + 30분(= 20분 전)이라 LATE가 됨
        mvc.perform(post("/api/criteria-versions").cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postMealMinutes\":30,\"measureSlots\":[\"LUNCH_AFTER\"],\"postMealTarget\":140}"))
                .andExpect(status().isCreated());
        createReading(access, 120, "POST_MEAL", now, mealId);   // 저장하면 그 알림에 이 혈당이 붙음

        mvc.perform(get("/api/today").cookie(access))
                .andExpect(jsonPath("$.data.readings[0].timing").value("ON_TIME"));   // 실제 알림(지금 + 10분) 기준
    }

    @Test
    void 예정보다_15분_넘게_일찍_재면_EARLY() throws Exception {
        assumeAwayFromMidnight();
        createCriteria(access, 60, 95, 140);
        Instant now = Instant.now();
        long mealId = createMeal(access, "LUNCH", now.minus(Duration.ofMinutes(90)));   // 예정 = 30분 전
        createReading(access, 120, "POST_MEAL", now.minus(Duration.ofMinutes(50)), mealId);   // 예정보다 20분 일찍

        mvc.perform(get("/api/today").cookie(access))
                .andExpect(jsonPath("$.data.readings[0].timing").value("EARLY"));
    }

    @Test
    void 오늘_새벽_혈당이_어젯밤_식사에_붙으면_어젯밤_식사_기준으로_계산한다() throws Exception {
        assumeTrue(LocalTime.now(ClockConfig.KST).isAfter(LocalTime.of(1, 0)), "오늘 00:40 기록을 만들려면 01시 이후여야 함");
        createCriteria(access, 60, 95, 140);
        long lateMeal = createMeal(access, "DINNER", kst(today().minusDays(1), 23, 30));   // 어젯밤 → 예정은 오늘 00:30
        createReading(access, 120, "POST_MEAL", kst(today(), 0, 40), lateMeal);           // 10분 뒤 → 제때

        mvc.perform(get("/api/today").cookie(access))
                .andExpect(jsonPath("$.data.meals", hasSize(0)))   // 어젯밤 식사는 오늘 식사 목록엔 없음
                .andExpect(jsonPath("$.data.readings", hasSize(1)))
                .andExpect(jsonPath("$.data.readings[0].timing").value("ON_TIME"));
    }

    @Test
    void 기준_없이_혈당만_적어도_criteria는_null() throws Exception {
        // 혈당을 저장하면 빈 기준 줄이 자동으로 생기지만, 사용자가 적은 기준이 아니라서 null로 나와야 함
        createReading(access, 100, "FASTING", Instant.now().minus(Duration.ofMinutes(1)), null);

        mvc.perform(get("/api/today").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.criteria").value((Object) null));
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
    void 기록_목록에_목표_판정과_재야_할_시각이_같이_온다() throws Exception {
        createCriteria(access, 60, 95, 140);
        LocalDate day = today().minusDays(1);
        createReading(access, 68, "FASTING", kst(day, 7, 0), null);              // 70 미만, 95 안 넘음
        long lunch = createMeal(access, "LUNCH", kst(day, 12, 0));               // 재야 할 시각 = 13:00
        createReading(access, 152, "POST_MEAL", kst(day, 13, 20), lunch);       // 20분 늦게, 140 넘음

        mvc.perform(get("/api/records").cookie(access)
                        .param("from", day.toString()).param("to", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].events[0].reading.belowLow").value(true))
                .andExpect(jsonPath("$.data.days[0].events[0].reading.aboveTarget").value(false))
                .andExpect(jsonPath("$.data.days[0].events[0].reading.targetValue").value(95))
                .andExpect(jsonPath("$.data.days[0].events[0].reading.expectedAt").value((Object) null))
                .andExpect(jsonPath("$.data.days[0].events[0].reading.meterLimit").value((Object) null))
                .andExpect(jsonPath("$.data.days[0].events[2].reading.aboveTarget").value(true))
                .andExpect(jsonPath("$.data.days[0].events[2].reading.belowLow").value(false))
                .andExpect(jsonPath("$.data.days[0].events[2].reading.targetValue").value(140))
                .andExpect(jsonPath("$.data.days[0].events[2].reading.expectedAt").value(kst(day, 13, 0).toString()))
                .andExpect(jsonPath("$.data.days[0].events[2].reading.timing").value("LATE"));
    }

    @Test
    void 재야_할_시각은_식사_날짜에_적용_중이던_기준으로_구한다() throws Exception {
        LocalDate day1 = today().minusDays(3);
        LocalDate day2 = today().minusDays(1);
        for (String body : List.of(
                "{\"postMealMinutes\":60,\"effectiveFrom\":\"" + day1.minusDays(1) + "\"}",
                "{\"postMealMinutes\":90,\"effectiveFrom\":\"" + day2 + "\"}",
                "{\"postMealMinutes\":120,\"effectiveFrom\":\"" + day2 + "\"}")) {   // 같은 적용일이면 나중 것(id 큰 것)
            mvc.perform(post("/api/criteria-versions").cookie(access).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());
        }
        long meal1 = createMeal(access, "LUNCH", kst(day1, 12, 0));
        createReading(access, 130, "POST_MEAL", kst(day1, 13, 0), meal1);
        long meal2 = createMeal(access, "LUNCH", kst(day2, 12, 0));
        createReading(access, 130, "POST_MEAL", kst(day2, 14, 0), meal2);

        mvc.perform(get("/api/records").cookie(access).param("from", day1.toString()).param("to", day2.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].events[1].reading.expectedAt").value(kst(day1, 13, 0).toString()))
                .andExpect(jsonPath("$.data.days[2].events[1].reading.expectedAt").value(kst(day2, 14, 0).toString()));
    }

    @Test
    void 값을_고치면_처음_값과_고친_시각이_남고_식사만_붙이면_수정으로_안_센다() throws Exception {
        LocalDate day = today().minusDays(1);
        long id = createReading(access, 128, "POST_MEAL", kst(day, 13, 12), null);
        long lunch = createMeal(access, "LUNCH", kst(day, 12, 0));
        mvc.perform(patch("/api/readings/" + id).cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mealId\":" + lunch + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editedAt").value((Object) null));

        mvc.perform(get("/api/records").cookie(access).param("from", day.toString()).param("to", day.toString()))
                .andExpect(jsonPath("$.data.days[0].events[1].id").value(id))
                .andExpect(jsonPath("$.data.days[0].events[1].modified").value(false));

        for (int v : new int[]{130, 124}) {   // 두 번 고쳐도 처음 값은 128
            mvc.perform(patch("/api/readings/" + id).cookie(access).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"value\":" + v + "}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/records").cookie(access).param("from", day.toString()).param("to", day.toString()))
                .andExpect(jsonPath("$.data.days[0].events[1].modified").value(true))
                .andExpect(jsonPath("$.data.days[0].events[1].reading.value").value(124))
                .andExpect(jsonPath("$.data.days[0].events[1].reading.originalValue").value(128))
                .andExpect(jsonPath("$.data.days[0].events[1].reading.editedAt").isNotEmpty());
    }

    @Test
    void 식사를_고치면_modified_true() throws Exception {
        LocalDate day = today().minusDays(1);
        long mealId = createMeal(access, "LUNCH", kst(day, 12, 0));
        mvc.perform(patch("/api/meals/" + mealId).cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"김밥\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/records").cookie(access)
                        .param("from", day.toString()).param("to", day.toString()))
                .andExpect(jsonPath("$.data.days[0].events[0].type").value("MEAL"))
                .andExpect(jsonPath("$.data.days[0].events[0].modified").value(true))
                .andExpect(jsonPath("$.data.days[0].events[0].meal.note").value("김밥"));
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
    void 달력에_진료일이_표시된다() throws Exception {
        YearMonth month = YearMonth.from(today()).plusMonths(1);   // 다음 달 (미래 날짜도 진료일이면 표시)
        createVisit(access, kst(month.atDay(10), 10, 30));
        createVisit(access, kst(month.atDay(15), 23, 30));   // 한국 밤 11시반도 15일
        createVisit(access, kst(month.atDay(16), 0, 10));    // 한국 0시 10분 = UTC로는 전날 15:10 → 한국 날짜 16일
        long removed = createVisit(access, kst(month.atDay(20), 9, 0));
        mvc.perform(delete("/api/visits/" + removed).cookie(access)).andExpect(status().isOk());
        createVisit(access, null);                            // 날짜 미정은 안 셈
        createVisit(other, kst(month.atDay(25), 9, 0));       // 남의 일정은 안 셈

        mvc.perform(get("/api/records/calendar").cookie(access).param("month", month.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[9].visit").value(true))     // 10일
                .andExpect(jsonPath("$.data.days[14].visit").value(true))    // 15일
                .andExpect(jsonPath("$.data.days[15].visit").value(true))    // 16일
                .andExpect(jsonPath("$.data.days[16].visit").value(false))   // 17일
                .andExpect(jsonPath("$.data.days[19].visit").value(false))   // 20일 (지운 일정)
                .andExpect(jsonPath("$.data.days[24].visit").value(false))   // 25일 (남의 일정)
                .andExpect(jsonPath("$.data.days[9].status").value("FUTURE"));
    }

    private long createVisit(Cookie who, Instant visitAt) throws Exception {
        String json = visitAt == null ? "{}" : "{\"visitAt\":\"" + visitAt + "\"}";
        String body = mvc.perform(post("/api/visits").cookie(who).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.visitId")).longValue();
    }

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
    void 가입_전_날짜에_적은_기록은_가려지지_않고_그_달로_갈_수_있다() throws Exception {
        LocalDate before = today().minusDays(70);   // 방금 가입했지만 70일 전 날짜로 기록
        createReading(access, 100, "FASTING", kst(before, 8, 0), null);
        YearMonth month = YearMonth.from(before);

        mvc.perform(get("/api/records/calendar").cookie(access).param("month", month.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstRecordDate").value(before.toString()))
                .andExpect(jsonPath("$.data.days[" + (before.getDayOfMonth() - 1) + "].status").value("RECORDED"))
                .andExpect(jsonPath("$.data.hasPrevMonth").value(false));   // 첫 기록이 있는 달이 맨 처음
        mvc.perform(get("/api/records/calendar").cookie(access).param("month", month.plusMonths(1).toString()))
                .andExpect(jsonPath("$.data.hasPrevMonth").value(true));
    }

    @Test
    void 남의_기록은_내_달력에_RECORDED로_안_잡힌다() throws Exception {
        signedUpDaysAgo("jisu@x.com", 40);
        LocalDate day = today().minusDays(10);
        createMeal(other, "LUNCH", kst(day, 12, 0));   // 다른 사람만 기록함
        YearMonth month = YearMonth.from(day);

        mvc.perform(get("/api/records/calendar").cookie(access).param("month", month.toString()))
                .andExpect(jsonPath("$.data.days[" + (day.getDayOfMonth() - 1) + "].status").value("EMPTY"))
                .andExpect(jsonPath("$.data.firstRecordDate").value((Object) null));
    }

    @Test
    void 다음_달은_전부_FUTURE이고_다음_달_없음() throws Exception {
        YearMonth next = YearMonth.from(today()).plusMonths(1);

        mvc.perform(get("/api/records/calendar").cookie(access).param("month", next.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].status").value("FUTURE"))
                .andExpect(jsonPath("$.data.days[" + (next.lengthOfMonth() - 1) + "].status").value("FUTURE"))
                .andExpect(jsonPath("$.data.hasPrevMonth").value(true))
                .andExpect(jsonPath("$.data.hasNextMonth").value(false));
    }

    @Test
    void 연도가_너무_크거나_작으면_500이_아니라_400() throws Exception {
        mvc.perform(get("/api/records").cookie(access)
                        .param("from", "+999999999-12-31").param("to", "+999999999-12-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        mvc.perform(get("/api/records/calendar").cookie(access).param("month", "1999-12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
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
