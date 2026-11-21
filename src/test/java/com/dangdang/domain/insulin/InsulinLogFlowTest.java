package com.dangdang.domain.insulin;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 인슐린 투여 기록 흐름 테스트
@SpringBootTest
@AutoConfigureMockMvc
class InsulinLogFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    static final String OCT_20_9AM = "2026-10-20T09:00:00+09:00";

    Cookie access;   // 지수(주인공)

    @BeforeEach
    void setUp() throws Exception {
        access = signupAndLogin("jisu@x.com");
    }

    @AfterEach
    void clean() { // users를 지우면 insulin_logs도 같이 지워짐 (ON DELETE CASCADE)
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    // ---------- 등록 ----------

    @Test
    void 종류와_단위와_맞은_시각을_보내면_201() throws Exception {
        String body = create(access, json("MEAL", "4.5", OCT_20_9AM))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.insulinLogId").exists())
                .andExpect(jsonPath("$.data.insulinType").value("MEAL"))
                .andExpect(jsonPath("$.data.units").value(4.5))
                .andExpect(jsonPath("$.data.takenAt").value(Instant.parse("2026-10-20T00:00:00Z").toString()))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.updatedAt").exists())
                .andReturn().getResponse().getContentAsString();

        long id = ((Number) JsonPath.read(body, "$.data.insulinLogId")).longValue();
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM insulin_logs WHERE id = ?", id);
        assertThat(row.get("insulin_type")).isEqualTo("MEAL");
        assertThat((BigDecimal) row.get("units")).isEqualByComparingTo("4.5");
        assertThat(row.get("deleted_at")).isNull();
    }

    @Test
    void 세_종류_모두_저장된다() throws Exception {
        create(access, json("MEAL", "6", OCT_20_9AM)).andExpect(status().isCreated());
        create(access, json("BEDTIME", "10", OCT_20_9AM)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.insulinType").value("BEDTIME"));
        create(access, json("CUSTOM", "2", OCT_20_9AM)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.insulinType").value("CUSTOM"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM insulin_logs", Long.class)).isEqualTo(3);
    }

    @Test
    void 단위는_0_초과_999_9_이하_소수_한_자리까지() throws Exception {
        create(access, json("MEAL", "0.1", OCT_20_9AM)).andExpect(status().isCreated());
        create(access, json("MEAL", "999.9", OCT_20_9AM)).andExpect(status().isCreated());
        create(access, json("MEAL", "6", OCT_20_9AM)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.units").value(6.0));

        create(access, json("MEAL", "0", OCT_20_9AM))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        create(access, json("MEAL", "-1", OCT_20_9AM)).andExpect(status().isBadRequest());
        create(access, json("MEAL", "1000", OCT_20_9AM)).andExpect(status().isBadRequest());
        create(access, json("MEAL", "4.55", OCT_20_9AM)).andExpect(status().isBadRequest());   // 소수 두 자리
        assertThat(jdbc.queryForObject("SELECT count(*) FROM insulin_logs", Long.class)).isEqualTo(3);
    }

    @Test
    void 맞은_시각은_2000년_이후부터_지금까지() throws Exception {
        String future = Instant.now().plus(1, ChronoUnit.HOURS).toString();
        create(access, json("MEAL", "4", future))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        create(access, json("MEAL", "4", "1999-12-31T23:59:00+09:00")).andExpect(status().isBadRequest());
        create(access, json("MEAL", "4", "-1000000000-01-01T00:00:00Z")).andExpect(status().isBadRequest());
        create(access, json("MEAL", "4", "2000-01-01T00:00:00+09:00")).andExpect(status().isCreated());   // 시작 경계
        create(access, json("MEAL", "4", Instant.now().minusSeconds(5).toString())).andExpect(status().isCreated());
    }

    @Test
    void 빠진_값이나_틀린_형식이면_400() throws Exception {
        create(access, "{\"units\":4,\"takenAt\":\"" + OCT_20_9AM + "\"}").andExpect(status().isBadRequest());
        create(access, "{\"insulinType\":\"MEAL\",\"takenAt\":\"" + OCT_20_9AM + "\"}").andExpect(status().isBadRequest());
        create(access, "{\"insulinType\":\"MEAL\",\"units\":4}").andExpect(status().isBadRequest());
        create(access, json("PUMP", "4", OCT_20_9AM)).andExpect(status().isBadRequest());
        create(access, "{\"insulinType\":\"MEAL\",\"units\":\"많이\",\"takenAt\":\"" + OCT_20_9AM + "\"}")
                .andExpect(status().isBadRequest());
        create(access, json("MEAL", "4", "2026-10-20 09:00")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM insulin_logs", Long.class)).isZero();
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mvc.perform(post("/api/insulin-logs").contentType(MediaType.APPLICATION_JSON)
                        .content(json("MEAL", "4", OCT_20_9AM)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 도우미 ----------

    private static String json(String type, String units, String takenAt) {
        return "{\"insulinType\":\"" + type + "\",\"units\":" + units + ",\"takenAt\":\"" + takenAt + "\"}";
    }

    private ResultActions create(Cookie who, String json) throws Exception {
        return mvc.perform(post("/api/insulin-logs").cookie(who).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Cookie signupAndLogin(String email) throws Exception {
        userRepository.save(User.ofEmail(email, "테스트", passwordEncoder.encode("abcd1234"), Instant.now()));
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }
}
