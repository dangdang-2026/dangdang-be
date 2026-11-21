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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 진료 리포트 흐름 테스트
@SpringBootTest
@AutoConfigureMockMvc
class VisitReportFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    Cookie access;   // 지수(주인공)
    Cookie other;    // 남의 것 확인용 다른 사용자
    long careSiteId;
    long visitId;

    @BeforeEach
    void setUp() throws Exception {
        access = signupAndLogin("jisu@x.com");
        other = signupAndLogin("minho@x.com");
        careSiteId = createCareSite(access);
        visitId = createVisit(access, "2026-10-14T10:00:00+09:00");
    }

    @AfterEach
    void clean() { // users를 지우면 saved_reports·visits·care_sites도 같이 지워짐 (ON DELETE CASCADE)
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    // ---------- 저장 ----------

    @Test
    void 기간_직접_리포트를_저장하면_201과_id() throws Exception {
        long id = saveAndGetId(access, custom("2026-10-01", "2026-10-14", careSiteId), 201);

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM saved_reports WHERE id = ?", id);
        assertThat(row.get("kind")).isEqualTo("CUSTOM");
        assertThat(row.get("from_date").toString()).isEqualTo("2026-10-01");
        assertThat(row.get("to_date").toString()).isEqualTo("2026-10-14");
        assertThat(row.get("visit_id")).isNull();
        assertThat(((Number) row.get("care_site_id")).longValue()).isEqualTo(careSiteId);
    }

    @Test
    void 진료용_리포트를_저장하면_201() throws Exception {
        long id = saveAndGetId(access, visit("2026-10-01", "2026-10-13", visitId), 201);
        assertThat(jdbc.queryForObject("SELECT visit_id FROM saved_reports WHERE id = ?", Long.class, id))
                .isEqualTo(visitId);
    }

    @Test
    void 같은_리포트를_다시_저장하면_새로_만들지_않고_기존_id를_200() throws Exception {
        long first = saveAndGetId(access, custom("2026-10-01", "2026-10-14", null), 201);
        long again = saveAndGetId(access, custom("2026-10-01", "2026-10-14", null), 200);
        assertThat(again).isEqualTo(first);

        long visitFirst = saveAndGetId(access, visit("2026-10-01", "2026-10-13", visitId), 201);
        long visitAgain = saveAndGetId(access, visit("2026-10-01", "2026-10-13", visitId), 200);
        assertThat(visitAgain).isEqualTo(visitFirst);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM saved_reports", Long.class)).isEqualTo(2);
    }

    @Test
    void 기간이나_종류나_진료_일정이_다르면_따로_저장된다() throws Exception {
        long base = saveAndGetId(access, custom("2026-10-01", "2026-10-14", null), 201);
        long otherTo = saveAndGetId(access, custom("2026-10-01", "2026-10-15", null), 201);
        long asVisit = saveAndGetId(access, visit("2026-10-01", "2026-10-14", visitId), 201);
        long otherVisit = saveAndGetId(access, visit("2026-10-01", "2026-10-14",
                createVisit(access, "2026-10-15T10:00:00+09:00")), 201);
        assertThat(java.util.Set.of(base, otherTo, asVisit, otherVisit)).hasSize(4);
    }

    @Test
    void 남이_같은_기간을_저장해도_내_리포트와_섞이지_않는다() throws Exception {
        long mine = saveAndGetId(access, custom("2026-10-01", "2026-10-14", null), 201);
        long others = saveAndGetId(other, custom("2026-10-01", "2026-10-14", null), 201);
        assertThat(others).isNotEqualTo(mine);
    }

    @Test
    void from이_to보다_늦으면_400() throws Exception {
        save(access, custom("2026-10-14", "2026-10-01", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        saveAndGetId(access, custom("2026-10-14", "2026-10-14", null), 201);   // 하루짜리는 됨
    }

    @Test
    void 기간이_300일을_넘거나_2000년에서_2100년_밖이면_400() throws Exception {
        saveAndGetId(access, custom("2026-01-01", "2026-10-27", null), 201);   // 300일 (끝 경계)
        save(access, custom("2026-01-01", "2026-10-28", null))                 // 301일
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        save(access, custom("1999-12-31", "2000-01-02", null)).andExpect(status().isBadRequest());
        save(access, custom("2100-12-31", "2101-01-01", null)).andExpect(status().isBadRequest());
        save(access, custom("+999999999-12-01", "+999999999-12-31", null)).andExpect(status().isBadRequest());
    }

    @Test
    void 종류와_진료_일정이_안_맞으면_400() throws Exception {
        save(access, "{\"kind\":\"VISIT\",\"from\":\"2026-10-01\",\"to\":\"2026-10-13\"}")   // 진료용인데 일정 없음
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        save(access, "{\"kind\":\"CUSTOM\",\"from\":\"2026-10-01\",\"to\":\"2026-10-13\",\"visitId\":" + visitId + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
    }

    @Test
    void 필수값이_없거나_형식이_틀리면_400() throws Exception {
        save(access, "{\"from\":\"2026-10-01\",\"to\":\"2026-10-13\"}").andExpect(status().isBadRequest());
        save(access, "{\"kind\":\"CUSTOM\",\"to\":\"2026-10-13\"}").andExpect(status().isBadRequest());
        save(access, "{\"kind\":\"CUSTOM\",\"from\":\"2026-10-01\"}").andExpect(status().isBadRequest());
        save(access, "{\"kind\":\"WEEKLY\",\"from\":\"2026-10-01\",\"to\":\"2026-10-13\"}").andExpect(status().isBadRequest());
        save(access, "{\"kind\":\"CUSTOM\",\"from\":\"2026/10/01\",\"to\":\"2026-10-13\"}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM saved_reports", Long.class)).isZero();
    }

    @Test
    void 남의_진료_일정이나_지운_일정이면_404() throws Exception {
        long othersVisit = createVisit(other, "2026-10-14T10:00:00+09:00");
        save(access, visit("2026-10-01", "2026-10-13", othersVisit))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("VISIT_40401"));

        mvc.perform(delete("/api/visits/" + visitId).cookie(access)).andExpect(status().isOk());
        save(access, visit("2026-10-01", "2026-10-13", visitId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("VISIT_40401"));
    }

    @Test
    void 남의_병원이나_지운_병원이면_404() throws Exception {
        long othersCareSite = createCareSite(other);
        save(access, custom("2026-10-01", "2026-10-14", othersCareSite))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CARE_SITE_40401"));

        mvc.perform(delete("/api/care-sites/" + careSiteId).cookie(access)).andExpect(status().isOk());
        save(access, custom("2026-10-01", "2026-10-14", careSiteId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CARE_SITE_40401"));
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mvc.perform(post("/api/visit-reports").contentType(MediaType.APPLICATION_JSON)
                        .content(custom("2026-10-01", "2026-10-14", null)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 진료 리포트 조회 ----------
    // 기록은 전부 지난 날짜로 만듦 (10/1 앞 진료, 10/14 이번 진료 → 기간 10/1~10/13)

    @Test
    void 진료_리포트는_앞_진료일부터_진료_전날까지_기록을_센다() throws Exception {
        createVisit(access, "2026-10-01T09:00:00+09:00");                 // 앞 진료
        mvc.perform(patch("/api/visits/" + visitId).cookie(access).contentType(MediaType.APPLICATION_JSON)
                .content("{\"careSiteId\":" + careSiteId + "}")).andExpect(status().isOk());
        criteria(access, "{\"fastingTarget\":95,\"postMealTarget\":140,\"postMealMinutes\":60,"
                + "\"measureSlots\":[\"FASTING\",\"LUNCH_AFTER\"],\"sourceNote\":\"9/1 안내\",\"effectiveFrom\":\"2026-09-01\"}");
        criteria(access, "{\"fastingTarget\":95,\"postMealTarget\":120,\"effectiveFrom\":\"2026-10-07\"}");
        jdbc.update("INSERT INTO pregnancies (user_id, due_date, state, created_at, updated_at)"
                + " VALUES (?, '2027-01-20', 'PREGNANT', now(), now())", userId("jisu@x.com"));

        reading(access, 100, "FASTING", "FASTING", "2026-09-30T08:00:00+09:00");     // 기간 앞 → 빠짐
        long first = reading(access, 96, "FASTING", "FASTING", "2026-10-01T08:00:00+09:00");   // 95 이상 → 목표 밖
        reading(access, 90, "FASTING", "FASTING", "2026-10-02T08:00:00+09:00");
        reading(access, 130, "POST_MEAL", "LUNCH_AFTER", "2026-10-03T13:00:00+09:00"); // 그때 기준 140 → 안
        reading(access, 130, "POST_MEAL", "LUNCH_AFTER", "2026-10-08T13:00:00+09:00"); // 바뀐 기준 120 → 밖
        reading(access, 65, "OTHER", null, "2026-10-09T20:00:00+09:00");               // 70 미만, 목표 비교 없음
        reading(access, 99, "FASTING", "FASTING", "2026-10-14T08:00:00+09:00");       // 진료 날 → 빠짐
        reading(other, 300, "FASTING", "FASTING", "2026-10-05T08:00:00+09:00");       // 남의 기록 → 빠짐
        jdbc.update("UPDATE readings SET entered_late = false WHERE id = ?", first);   // 나머지는 지금 적어서 늦게 적음

        long jisu = userId("jisu@x.com");
        reminder(jisu, "FASTING", "2026-10-05T08:00:00+09:00", "SENT");         // 빈칸
        reminder(jisu, "DINNER_AFTER", "2026-10-06T19:00:00+09:00", "OPENED");  // 빈칸
        reminder(jisu, "FASTING", "2026-10-07T08:00:00+09:00", "DONE");         // 잼 → 안 셈
        reminder(jisu, "FASTING", "2026-09-30T08:00:00+09:00", "SENT");         // 기간 밖
        reminder(jisu, "FASTING", "2026-10-14T08:00:00+09:00", "SENT");         // 기간 밖

        question(visitId, "공복 수치가 괜찮은지");
        question(visitId, "식후 2시간으로 바꿔도 되는지");

        report(access, visitId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("VISIT"))
                .andExpect(jsonPath("$.data.from").value("2026-10-01"))
                .andExpect(jsonPath("$.data.to").value("2026-10-13"))
                .andExpect(jsonPath("$.data.visitId").value(visitId))
                .andExpect(jsonPath("$.data.visitDate").value("2026-10-14"))
                .andExpect(jsonPath("$.data.careSite.careSiteId").value(careSiteId))
                .andExpect(jsonPath("$.data.careSite.name").value("당당산부인과"))
                .andExpect(jsonPath("$.data.displayName").value("테스트"))
                .andExpect(jsonPath("$.data.pregnancyWeeks.from").value(24))
                .andExpect(jsonPath("$.data.pregnancyWeeks.to").value(25))
                // 기준: 시작일에 적용 중이던 9/1 기준 + 기간 안에 바뀐 10/7 기준
                .andExpect(jsonPath("$.data.criteria", hasSize(2)))
                .andExpect(jsonPath("$.data.criteria[0].effectiveFrom").value("2026-09-01"))
                .andExpect(jsonPath("$.data.criteria[0].postMealTarget").value(140))
                .andExpect(jsonPath("$.data.criteria[0].postMealMinutes").value(60))
                .andExpect(jsonPath("$.data.criteria[0].measureSlots", contains("FASTING", "LUNCH_AFTER")))
                .andExpect(jsonPath("$.data.criteria[0].sourceNote").value("9/1 안내"))
                .andExpect(jsonPath("$.data.criteria[1].effectiveFrom").value("2026-10-07"))
                .andExpect(jsonPath("$.data.criteria[1].postMealTarget").value(120))
                .andExpect(jsonPath("$.data.summary.measured").value(5))
                .andExpect(jsonPath("$.data.summary.missed").value(2))
                .andExpect(jsonPath("$.data.summary.enteredLate").value(4))
                .andExpect(jsonPath("$.data.summary.below70").value(1))
                .andExpect(jsonPath("$.data.summary.outOfTarget").value(2))
                .andExpect(jsonPath("$.data.summary.inTargetRate").value(0.5))   // 목표와 비교한 4개 중 2개
                .andExpect(jsonPath("$.data.summary.compared").value(true))
                // 잴 때별: 하루 순서, 잴 때 없는 기록은 맨 뒤
                .andExpect(jsonPath("$.data.slots", hasSize(3)))
                .andExpect(jsonPath("$.data.slots[0].slot").value("FASTING"))
                .andExpect(jsonPath("$.data.slots[0].measured").value(2))
                .andExpect(jsonPath("$.data.slots[0].outOfTarget").value(1))
                .andExpect(jsonPath("$.data.slots[1].slot").value("LUNCH_AFTER"))
                .andExpect(jsonPath("$.data.slots[1].measured").value(2))
                .andExpect(jsonPath("$.data.slots[1].outOfTarget").value(1))
                .andExpect(jsonPath("$.data.slots[2].slot").value(nullValue()))
                .andExpect(jsonPath("$.data.slots[2].measured").value(1))
                // 원값 표: 잰 시각 오래된 순
                .andExpect(jsonPath("$.data.readings", hasSize(5)))
                .andExpect(jsonPath("$.data.readings[0].readingId").value(first))
                .andExpect(jsonPath("$.data.readings[0].value").value(96))
                .andExpect(jsonPath("$.data.readings[0].aboveTarget").value(true))
                .andExpect(jsonPath("$.data.readings[0].enteredLate").value(false))
                .andExpect(jsonPath("$.data.readings[2].aboveTarget").value(false))
                .andExpect(jsonPath("$.data.readings[4].readingType").value("OTHER"))
                .andExpect(jsonPath("$.data.readings[4].aboveTarget").value(nullValue()))
                .andExpect(jsonPath("$.data.questionCount").value(2));
    }

    @Test
    void 앞_진료가_없으면_첫_기록일부터이고_기록도_없으면_전날_하루() throws Exception {
        report(access, visitId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.from").value("2026-10-13"))
                .andExpect(jsonPath("$.data.to").value("2026-10-13"))
                .andExpect(jsonPath("$.data.summary.measured").value(0))
                .andExpect(jsonPath("$.data.summary.inTargetRate").value(nullValue()))
                .andExpect(jsonPath("$.data.summary.compared").value(false))
                .andExpect(jsonPath("$.data.slots", hasSize(0)))
                .andExpect(jsonPath("$.data.readings", hasSize(0)))
                .andExpect(jsonPath("$.data.criteria", hasSize(0)))
                .andExpect(jsonPath("$.data.careSite").value(nullValue()))
                .andExpect(jsonPath("$.data.pregnancyWeeks").value(nullValue()))
                .andExpect(jsonPath("$.data.questionCount").value(0));

        reading(access, 110, "FASTING", "FASTING", "2026-10-05T08:00:00+09:00");
        report(access, visitId)
                .andExpect(jsonPath("$.data.from").value("2026-10-05"))
                .andExpect(jsonPath("$.data.summary.measured").value(1))
                .andExpect(jsonPath("$.data.summary.compared").value(false));   // 기준이 없으면 비교 안 함
    }

    @Test
    void 같은_날_진료는_앞_진료로_치지_않고_지운_진료도_안_친다() throws Exception {
        createVisit(access, "2026-10-14T08:00:00+09:00");   // 같은 날 더 이른 시각
        long deleted = createVisit(access, "2026-10-10T09:00:00+09:00");
        mvc.perform(delete("/api/visits/" + deleted).cookie(access)).andExpect(status().isOk());
        createVisit(access, "2026-10-03T09:00:00+09:00");

        report(access, visitId).andExpect(jsonPath("$.data.from").value("2026-10-03"));
    }

    @Test
    void 기간이_300일을_넘으면_끝에서_300일만() throws Exception {
        reading(access, 90, "FASTING", "FASTING", "2025-11-01T08:00:00+09:00");
        report(access, visitId)
                .andExpect(jsonPath("$.data.from").value(LocalDate.of(2026, 10, 13).minusDays(299).toString()))
                .andExpect(jsonPath("$.data.summary.measured").value(0));
    }

    @Test
    void 진료_날짜가_미정이면_400() throws Exception {
        long undecided = createVisitWithoutDate(access);
        report(access, undecided)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VISIT_40003"));
    }

    @Test
    void 남의_일정이나_지운_일정의_리포트는_404() throws Exception {
        long othersVisit = createVisit(other, "2026-10-14T10:00:00+09:00");
        report(access, othersVisit)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("VISIT_40401"));

        mvc.perform(delete("/api/visits/" + visitId).cookie(access)).andExpect(status().isOk());
        report(access, visitId).andExpect(status().isNotFound());
    }

    @Test
    void 지운_병원은_리포트에_안_나온다() throws Exception {
        mvc.perform(patch("/api/visits/" + visitId).cookie(access).contentType(MediaType.APPLICATION_JSON)
                .content("{\"careSiteId\":" + careSiteId + "}")).andExpect(status().isOk());
        mvc.perform(delete("/api/care-sites/" + careSiteId).cookie(access)).andExpect(status().isOk());
        report(access, visitId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.careSite").value(nullValue()));
    }

    // ---------- 저장한 리포트 조회 ----------

    @Test
    void 기간_직접_리포트는_저장한_기간과_병원으로_다시_계산한다() throws Exception {
        reading(access, 90, "FASTING", "FASTING", "2026-10-01T08:00:00+09:00");   // 기간 앞
        reading(access, 91, "FASTING", "FASTING", "2026-10-05T08:00:00+09:00");
        long id = saveAndGetId(access, custom("2026-10-02", "2026-10-08", careSiteId), 201);
        reading(access, 92, "FASTING", "FASTING", "2026-10-06T08:00:00+09:00");   // 저장 뒤에 적은 기록도 들어감

        savedReport(access, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("CUSTOM"))
                .andExpect(jsonPath("$.data.from").value("2026-10-02"))
                .andExpect(jsonPath("$.data.to").value("2026-10-08"))
                .andExpect(jsonPath("$.data.visitId").value(nullValue()))
                .andExpect(jsonPath("$.data.visitDate").value(nullValue()))
                .andExpect(jsonPath("$.data.questionCount").value(nullValue()))
                .andExpect(jsonPath("$.data.careSite.name").value("당당산부인과"))
                .andExpect(jsonPath("$.data.summary.measured").value(2))
                .andExpect(jsonPath("$.data.readings[0].value").value(91));
    }

    @Test
    void 진료용_리포트는_저장한_기간에_진료_정보를_붙인다() throws Exception {
        question(visitId, "공복 수치가 괜찮은지");
        reading(access, 91, "FASTING", "FASTING", "2026-10-05T08:00:00+09:00");
        long id = saveAndGetId(access, visit("2026-10-01", "2026-10-13", visitId), 201);

        savedReport(access, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("VISIT"))
                .andExpect(jsonPath("$.data.from").value("2026-10-01"))
                .andExpect(jsonPath("$.data.to").value("2026-10-13"))
                .andExpect(jsonPath("$.data.visitId").value(visitId))
                .andExpect(jsonPath("$.data.visitDate").value("2026-10-14"))
                .andExpect(jsonPath("$.data.questionCount").value(1))
                .andExpect(jsonPath("$.data.summary.measured").value(1));

        // 저장 뒤 진료 날짜를 미정으로 바꿔도 저장한 기간으로 보여 줌
        mvc.perform(patch("/api/visits/" + visitId).cookie(access).contentType(MediaType.APPLICATION_JSON)
                .content("{\"visitAt\":null}")).andExpect(status().isOk());
        savedReport(access, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.from").value("2026-10-01"))
                .andExpect(jsonPath("$.data.visitDate").value(nullValue()));
    }

    @Test
    void 남의_리포트나_없는_리포트나_일정을_지운_진료용_리포트는_404() throws Exception {
        long others = saveAndGetId(other, custom("2026-10-01", "2026-10-14", null), 201);
        savedReport(access, others)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("VISIT_40404"));
        savedReport(access, others + 1000).andExpect(status().isNotFound());

        long id = saveAndGetId(access, visit("2026-10-01", "2026-10-13", visitId), 201);
        mvc.perform(delete("/api/visits/" + visitId).cookie(access)).andExpect(status().isOk());
        savedReport(access, id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("VISIT_40404"));
    }

    // ---------- 도우미 ----------

    private ResultActions savedReport(Cookie who, long reportId) throws Exception {
        return mvc.perform(get("/api/visit-reports/" + reportId).cookie(who));
    }

    private ResultActions report(Cookie who, long visit) throws Exception {
        return mvc.perform(get("/api/visits/" + visit + "/report").cookie(who));
    }

    private void criteria(Cookie who, String json) throws Exception {
        mvc.perform(post("/api/criteria-versions").cookie(who).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    private long reading(Cookie who, int value, String type, String slot, String measuredAt) throws Exception {
        String body = mvc.perform(post("/api/readings").cookie(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":" + value + ",\"measuredAt\":\"" + measuredAt + "\",\"readingType\":\"" + type + "\""
                                + (slot == null ? "" : ",\"slot\":\"" + slot + "\"") + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.readingId")).longValue();
    }

    private void reminder(long userId, String slot, String scheduledAt, String status) {
        jdbc.update("INSERT INTO reminders (user_id, slot, scheduled_at, status) VALUES (?, ?, ?, ?)",
                userId, slot, Timestamp.from(OffsetDateTime.parse(scheduledAt).toInstant()), status);
    }

    private void question(long visit, String content) throws Exception {
        mvc.perform(post("/api/visits/" + visit + "/questions").cookie(access).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + content + "\"}")).andExpect(status().isCreated());
    }

    private long userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long createVisitWithoutDate(Cookie who) throws Exception {
        String body = mvc.perform(post("/api/visits").cookie(who).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.visitId")).longValue();
    }

    private static String custom(String from, String to, Long careSiteId) {
        return "{\"kind\":\"CUSTOM\",\"from\":\"" + from + "\",\"to\":\"" + to + "\""
                + (careSiteId != null ? ",\"careSiteId\":" + careSiteId : "") + "}";
    }

    private static String visit(String from, String to, long visitId) {
        return "{\"kind\":\"VISIT\",\"from\":\"" + from + "\",\"to\":\"" + to + "\",\"visitId\":" + visitId + "}";
    }

    private ResultActions save(Cookie who, String json) throws Exception {
        return mvc.perform(post("/api/visit-reports").cookie(who).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long saveAndGetId(Cookie who, String json, int expectedStatus) throws Exception {
        String body = save(who, json).andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private Cookie signupAndLogin(String email) throws Exception {
        userRepository.save(User.ofEmail(email, "테스트", passwordEncoder.encode("abcd1234"), Instant.now()));
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    private long createCareSite(Cookie who) throws Exception {
        String body = mvc.perform(post("/api/care-sites").cookie(who)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"당당산부인과\",\"department\":\"OBGYN\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.careSiteId")).longValue();
    }

    private long createVisit(Cookie who, String visitAt) throws Exception {
        String body = mvc.perform(post("/api/visits").cookie(who)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visitAt\":\"" + visitAt + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.visitId")).longValue();
    }
}
