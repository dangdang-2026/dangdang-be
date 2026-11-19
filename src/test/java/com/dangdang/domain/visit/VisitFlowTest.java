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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 진료 일정 흐름 테스트
@SpringBootTest
@AutoConfigureMockMvc
class VisitFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    static final String DEC_3 = "2026-12-03T10:30:00+09:00";
    static final String DEC_17 = "2026-12-17T14:00:00+09:00";

    Cookie access;   // 지수(주인공)
    Cookie other;    // 남의 일정 확인용 다른 사용자
    long careSiteId;

    @BeforeEach
    void setUp() throws Exception {
        access = signupAndLogin("jisu@x.com");
        other = signupAndLogin("minho@x.com");
        careSiteId = createCareSite(access);
    }

    @AfterEach
    void clean() { // users를 지우면 visits·care_sites도 같이 지워짐 (ON DELETE CASCADE)
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    // ---------- 등록 ----------

    @Test
    void 병원과_날짜를_골라_등록하면_201() throws Exception {
        create(access, "{\"careSiteId\":" + careSiteId + ",\"visitAt\":\"" + DEC_3 + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.visitId").exists())
                .andExpect(jsonPath("$.data.careSiteId").value(careSiteId))
                .andExpect(jsonPath("$.data.visitAt").value(Instant.parse("2026-12-03T01:30:00Z").toString()))
                .andExpect(jsonPath("$.data.createdAt").exists());
    }

    @Test
    void 병원과_날짜를_몰라도_등록된다() throws Exception {
        create(access, "{}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.careSiteId").value(nullValue()))
                .andExpect(jsonPath("$.data.visitAt").value(nullValue()));
    }

    @Test
    void 남의_병원이나_지운_병원이면_404() throws Exception {
        long othersCareSite = createCareSite(other);
        create(access, "{\"careSiteId\":" + othersCareSite + "}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CARE_SITE_40401"));

        mvc.perform(delete("/api/care-sites/" + careSiteId).cookie(access)).andExpect(status().isOk());
        create(access, "{\"careSiteId\":" + careSiteId + "}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CARE_SITE_40401"));
    }

    @Test
    void 진료_날짜가_2000년에서_2100년_밖이면_400() throws Exception {
        create(access, "{\"visitAt\":\"+300000-01-01T00:00:00Z\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        create(access, "{\"visitAt\":\"+1000000000-12-31T23:59:59Z\"}")   // Instant 끝 값 (시간대를 바꾸면 터지는 값)
                .andExpect(status().isBadRequest());
        create(access, "{\"visitAt\":\"-1000000000-01-01T00:00:00Z\"}").andExpect(status().isBadRequest());
        create(access, "{\"visitAt\":\"2100-12-31T23:59:00+09:00\"}").andExpect(status().isCreated());   // 끝 경계
        create(access, "{\"visitAt\":\"1999-12-31T09:00:00+09:00\"}").andExpect(status().isBadRequest());
        create(access, "{\"visitAt\":\"2101-01-01T09:00:00+09:00\"}").andExpect(status().isBadRequest());
        create(access, "{\"visitAt\":\"2000-01-01T00:00:00+09:00\"}").andExpect(status().isCreated());

        long id = createAndGetId(access, "{}");
        update(access, id, "{\"visitAt\":\"+300000-01-01T00:00:00Z\"}").andExpect(status().isBadRequest());
    }

    // ---------- 목록 ----------

    @Test
    void 목록은_진료_날짜_이른_순이고_날짜_미정은_맨_뒤() throws Exception {
        long undecided = createAndGetId(access, "{}");
        long late = createAndGetId(access, "{\"visitAt\":\"" + DEC_17 + "\"}");
        long early = createAndGetId(access, "{\"visitAt\":\"" + DEC_3 + "\"}");

        list(access, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].visitId").value(contains((int) early, (int) late, (int) undecided)))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 목록을_페이지로_나눠서_본다() throws Exception {
        createAndGetId(access, "{\"visitAt\":\"" + DEC_3 + "\"}");
        createAndGetId(access, "{\"visitAt\":\"" + DEC_17 + "\"}");
        createAndGetId(access, "{}");

        list(access, "?page=0&size=2")
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        list(access, "?page=1&size=2")
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 목록에_남의_일정과_지운_일정은_안_나온다() throws Exception {
        long mine = createAndGetId(access, "{}");
        long removed = createAndGetId(access, "{}");
        createAndGetId(other, "{}");
        mvc.perform(delete("/api/visits/" + removed).cookie(access)).andExpect(status().isOk());

        list(access, "")
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].visitId").value(mine));
    }

    @Test
    void 페이지_범위가_틀리면_400() throws Exception {
        list(access, "?size=0").andExpect(status().isBadRequest());
        list(access, "?size=101").andExpect(status().isBadRequest());
        list(access, "?page=-1").andExpect(status().isBadRequest());
        list(access, "?page=10001").andExpect(status().isBadRequest());
    }

    // ---------- 상세 ----------

    @Test
    void 상세에는_질문과_메모가_같이_나온다() throws Exception {
        long id = createAndGetId(access, "{\"careSiteId\":" + careSiteId + ",\"visitAt\":\"" + DEC_3 + "\"}");

        mvc.perform(get("/api/visits/" + id).cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visitId").value(id))
                .andExpect(jsonPath("$.data.careSiteId").value(careSiteId))
                .andExpect(jsonPath("$.data.questions", hasSize(0)))
                .andExpect(jsonPath("$.data.notes", hasSize(0)));
    }

    @Test
    void 남의_일정이나_없는_일정을_보면_404() throws Exception {
        long othersVisit = createAndGetId(other, "{}");
        mvc.perform(get("/api/visits/" + othersVisit).cookie(access))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("VISIT_40401"));
        mvc.perform(get("/api/visits/999999999").cookie(access))
                .andExpect(status().isNotFound());
    }

    // ---------- 수정 ----------

    @Test
    void 보낸_칸만_바뀐다() throws Exception {
        long id = createAndGetId(access, "{\"careSiteId\":" + careSiteId + ",\"visitAt\":\"" + DEC_3 + "\"}");

        update(access, id, "{\"visitAt\":\"" + DEC_17 + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visitAt").value(Instant.parse("2026-12-17T05:00:00Z").toString()))
                .andExpect(jsonPath("$.data.careSiteId").value(careSiteId));   // 안 보낸 병원은 그대로
    }

    @Test
    void null을_보내면_날짜_미정과_병원_없음으로_바뀐다() throws Exception {
        long id = createAndGetId(access, "{\"careSiteId\":" + careSiteId + ",\"visitAt\":\"" + DEC_3 + "\"}");

        update(access, id, "{\"visitAt\":null}")
                .andExpect(jsonPath("$.data.visitAt").value(nullValue()))
                .andExpect(jsonPath("$.data.careSiteId").value(careSiteId));
        update(access, id, "{\"careSiteId\":null}")
                .andExpect(jsonPath("$.data.careSiteId").value(nullValue()));
    }

    @Test
    void 병원이_지워졌어도_같은_병원_id를_다시_보내면_수정된다() throws Exception {
        long id = createAndGetId(access, "{\"careSiteId\":" + careSiteId + ",\"visitAt\":\"" + DEC_3 + "\"}");
        mvc.perform(delete("/api/care-sites/" + careSiteId).cookie(access)).andExpect(status().isOk());

        // 화면이 원래 값을 그대로 같이 보내는 경우
        update(access, id, "{\"careSiteId\":" + careSiteId + ",\"visitAt\":\"" + DEC_17 + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.careSiteId").value(careSiteId))
                .andExpect(jsonPath("$.data.visitAt").value(Instant.parse("2026-12-17T05:00:00Z").toString()));

        // 지운 병원으로 새로 바꾸는 건 안 됨
        long another = createAndGetId(access, "{}");
        update(access, another, "{\"careSiteId\":" + careSiteId + "}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CARE_SITE_40401"));
    }

    @Test
    void 바꿀_값이_없으면_400() throws Exception {
        long id = createAndGetId(access, "{}");
        update(access, id, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VISIT_40001"));
    }

    @Test
    void 남의_병원으로_바꾸거나_남의_일정을_고치면_404() throws Exception {
        long id = createAndGetId(access, "{}");
        long othersCareSite = createCareSite(other);
        update(access, id, "{\"careSiteId\":" + othersCareSite + "}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CARE_SITE_40401"));

        long othersVisit = createAndGetId(other, "{}");
        update(access, othersVisit, "{\"visitAt\":\"" + DEC_3 + "\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("VISIT_40401"));
    }

    // ---------- 삭제 ----------

    @Test
    void 지우면_상세가_404이고_또_지워도_404() throws Exception {
        long id = createAndGetId(access, "{}");

        mvc.perform(delete("/api/visits/" + id).cookie(access)).andExpect(status().isOk());
        mvc.perform(get("/api/visits/" + id).cookie(access)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/visits/" + id).cookie(access))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("VISIT_40401"));
    }

    @Test
    void 남의_일정은_못_지운다() throws Exception {
        long othersVisit = createAndGetId(other, "{}");
        mvc.perform(delete("/api/visits/" + othersVisit).cookie(access)).andExpect(status().isNotFound());
        mvc.perform(get("/api/visits/" + othersVisit).cookie(other)).andExpect(status().isOk());
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mvc.perform(get("/api/visits")).andExpect(status().isUnauthorized());
    }

    // ---------- 도우미 ----------

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

    private ResultActions create(Cookie who, String json) throws Exception {
        return mvc.perform(post("/api/visits").cookie(who).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long createAndGetId(Cookie who, String json) throws Exception {
        String body = create(who, json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.visitId")).longValue();
    }

    private ResultActions list(Cookie who, String query) throws Exception {
        return mvc.perform(get("/api/visits" + query).cookie(who));
    }

    private ResultActions update(Cookie who, long visitId, String json) throws Exception {
        return mvc.perform(patch("/api/visits/" + visitId).cookie(who)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
