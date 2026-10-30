package com.dangdang.domain.criteria;

import com.dangdang.domain.criteria.entity.CriteriaVersion;
import com.dangdang.domain.criteria.service.CriteriaService;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.config.ClockConfig;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CriteriaVersionFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;
    @Autowired CriteriaService criteriaService;

    Cookie access;
    Long userId;

    @BeforeEach
    void loginFirst() throws Exception {
        userId = userRepository
                .save(User.ofEmail("jisu@x.com", "지수", passwordEncoder.encode("abcd1234"), Instant.now()))
                .getId();
        access = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jisu@x.com\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    @AfterEach
    void clean() { // users를 지우면 criteria_versions도 같이 지워진다 (ON DELETE CASCADE)
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    // 201을 기대하는 등록 도우미
    private void create(String body) throws Exception {
        mvc.perform(post("/api/criteria-versions").cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private static String today() {
        return LocalDate.now(ClockConfig.KST).toString();
    }

    @Test
    void 등록하면_201이고_지금_기준으로_보인다() throws Exception {
        mvc.perform(post("/api/criteria-versions").cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postMealMinutes\":60,\"measureSlots\":[\"FASTING\",\"LUNCH_AFTER\"],"
                                + "\"fastingTarget\":95,\"postMealTarget\":140}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.effectiveFrom").value(today()));

        mvc.perform(get("/api/criteria-versions/current").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postMealMinutes").value(60))
                .andExpect(jsonPath("$.data.measureSlots[1]").value("LUNCH_AFTER"));
    }

    @Test
    void 두_번_등록하면_덮어쓰지_않고_쌓이고_최신이_current() throws Exception {
        create("{\"postMealMinutes\":60}");
        create("{\"postMealMinutes\":90}");
        mvc.perform(get("/api/criteria-versions").cookie(access))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].postMealMinutes").value(90));
        mvc.perform(get("/api/criteria-versions/current").cookie(access))
                .andExpect(jsonPath("$.data.postMealMinutes").value(90));
    }

    @Test
    void 내일부터_적용하는_기준은_오늘_current가_아니다() throws Exception {
        String tomorrow = LocalDate.now(ClockConfig.KST).plusDays(1).toString();
        create("{\"postMealMinutes\":60}");
        create("{\"postMealMinutes\":120,\"effectiveFrom\":\"" + tomorrow + "\"}");
        mvc.perform(get("/api/criteria-versions/current").cookie(access))
                .andExpect(jsonPath("$.data.postMealMinutes").value(60));
        mvc.perform(get("/api/criteria-versions").cookie(access))
                .andExpect(jsonPath("$.data[0].effectiveFrom").value(tomorrow));
    }

    @Test
    void 아직_모름이면_빈칸으로_저장된다() throws Exception {
        create("{\"postMealMinutes\":null}");
        mvc.perform(get("/api/criteria-versions/current").cookie(access))
                .andExpect(jsonPath("$.data.criteriaVersionId").exists())
                .andExpect(jsonPath("$.data.postMealMinutes").doesNotExist());
    }

    @Test
    void 십분_단위가_아니거나_범위_밖이면_400() throws Exception {
        for (String body : List.of(
                "{\"postMealMinutes\":95}", "{\"postMealMinutes\":20}",
                "{\"postMealMinutes\":200}", "{\"fastingTarget\":0}")) {
            mvc.perform(post("/api/criteria-versions").cookie(access)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        }
    }

    @Test
    void 기준이_없으면_current는_data가_없다() throws Exception {
        mvc.perform(get("/api/criteria-versions/current").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void currentOrEmpty는_없으면_빈_기준을_한_번만_만든다() {
        CriteriaVersion first = criteriaService.currentOrEmpty(userId);
        CriteriaVersion second = criteriaService.currentOrEmpty(userId);
        assertThat(first.getPostMealMinutes()).isNull();
        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mvc.perform(get("/api/criteria-versions/current"))
                .andExpect(status().isUnauthorized());
    }
}