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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    // ---------- 도우미 ----------

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
