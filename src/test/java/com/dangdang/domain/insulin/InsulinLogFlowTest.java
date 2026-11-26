package com.dangdang.domain.insulin;

import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.support.TestConsents;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    Cookie other;    // 남의 기록 확인용 다른 사용자

    @BeforeEach
    void setUp() throws Exception {
        access = signupAndLogin("jisu@x.com");
        other = signupAndLogin("minho@x.com");
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
    void 식사_때는_끼니와_함께_자기_전은_끼니_없이_저장된다() throws Exception {
        long meal = createAndGetId(access, json("MEAL", "DINNER", "6", OCT_20_9AM));
        long bedtime = createAndGetId(access, json("BEDTIME", null, "10", OCT_20_9AM));

        list(access, "?from=2026-10-20&to=2026-10-20")
                .andExpect(jsonPath("$.data.items[0].insulinLogId").value(meal))
                .andExpect(jsonPath("$.data.items[0].mealSlot").value("DINNER"))
                .andExpect(jsonPath("$.data.items[1].insulinLogId").value(bedtime))
                .andExpect(jsonPath("$.data.items[1].mealSlot").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT meal_slot FROM insulin_logs WHERE id = ?", String.class, meal))
                .isEqualTo("DINNER");
        assertThat(jdbc.queryForObject("SELECT meal_slot FROM insulin_logs WHERE id = ?", String.class, bedtime))
                .isNull();
    }

    @Test
    void 끼니가_종류와_안_맞으면_400() throws Exception {
        create(access, json("MEAL", null, "6", OCT_20_9AM))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INSULIN_40003"));
        create(access, json("BEDTIME", "LUNCH", "10", OCT_20_9AM))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INSULIN_40004"));
        create(access, json("MEAL", "SNACK", "6", OCT_20_9AM))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INSULIN_40005"));
        create(access, json("MEAL", "BRUNCH", "6", OCT_20_9AM))     // 없는 끼니
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        create(access, json("CUSTOM", null, "2", OCT_20_9AM))       // 직접 입력은 빠짐
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM insulin_logs", Long.class)).isZero();
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

    // ---------- 목록 ----------

    @Test
    void 목록은_한국_날짜_기간_안_기록을_맞은_시각_순으로() throws Exception {
        create(access, json("MEAL", "1", "2026-10-19T23:59:00+09:00")).andExpect(status().isCreated());   // 기간 앞
        long late = createAndGetId(access, json("BEDTIME", "10", "2026-10-21T23:59:00+09:00"));        // 끝 날 마지막
        long early = createAndGetId(access, json("MEAL", "4", "2026-10-20T00:00:00+09:00"));           // 시작 날 0시
        create(access, json("MEAL", "2", "2026-10-22T00:00:00+09:00")).andExpect(status().isCreated());   // 기간 뒤

        list(access, "?from=2026-10-20&to=2026-10-21")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].insulinLogId").value(early))
                .andExpect(jsonPath("$.data.items[0].units").value(4.0))
                .andExpect(jsonPath("$.data.items[1].insulinLogId").value(late))
                .andExpect(jsonPath("$.data.items[1].insulinType").value("BEDTIME"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 남의_기록과_지운_기록은_목록에_없다() throws Exception {
        long mine = createAndGetId(access, json("MEAL", "4", OCT_20_9AM));
        long deleted = createAndGetId(access, json("MEAL", "5", OCT_20_9AM));
        jdbc.update("UPDATE insulin_logs SET deleted_at = now() WHERE id = ?", deleted);
        create(other, json("MEAL", "6", OCT_20_9AM)).andExpect(status().isCreated());

        list(access, "?from=2026-10-20&to=2026-10-20")
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].insulinLogId").value(mine));
    }

    @Test
    void 목록은_페이지로_나뉜다() throws Exception {
        long first = createAndGetId(access, json("MEAL", "1", "2026-10-20T08:00:00+09:00"));
        long second = createAndGetId(access, json("MEAL", "2", "2026-10-20T12:00:00+09:00"));
        long third = createAndGetId(access, json("BEDTIME", "3", "2026-10-20T22:00:00+09:00"));

        list(access, "?from=2026-10-20&to=2026-10-20&size=2")
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].insulinLogId").value(first))
                .andExpect(jsonPath("$.data.items[1].insulinLogId").value(second))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        list(access, "?from=2026-10-20&to=2026-10-20&size=2&page=1")
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].insulinLogId").value(third))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 기록이_없으면_빈_배열() throws Exception {
        list(access, "?from=2026-10-20&to=2026-10-20")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 기간이나_페이지가_틀리면_400() throws Exception {
        list(access, "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        list(access, "?from=2026-10-20").andExpect(status().isBadRequest());
        list(access, "?from=2026/10/20&to=2026-10-21").andExpect(status().isBadRequest());
        list(access, "?from=2026-10-21&to=2026-10-20")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INSULIN_40001"));
        list(access, "?from=1999-12-31&to=2026-10-20").andExpect(status().isBadRequest());
        list(access, "?from=2026-10-20&to=2026-10-20&page=-1").andExpect(status().isBadRequest());
        list(access, "?from=2026-10-20&to=2026-10-20&size=0").andExpect(status().isBadRequest());
        list(access, "?from=2026-10-20&to=2026-10-20&size=101").andExpect(status().isBadRequest());
        list(access, "?from=2026-10-20&to=2026-10-20").andExpect(status().isOk());   // 하루짜리는 됨
    }

    // ---------- 수정 ----------

    @Test
    void 보낸_칸만_바뀌고_나머지는_그대로() throws Exception {
        long id = createAndGetId(access, json("MEAL", "4", OCT_20_9AM));

        update(access, id, "{\"units\":4.5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.insulinLogId").value(id))
                .andExpect(jsonPath("$.data.units").value(4.5))
                .andExpect(jsonPath("$.data.insulinType").value("MEAL"))
                .andExpect(jsonPath("$.data.takenAt").value(Instant.parse("2026-10-20T00:00:00Z").toString()));

        // 자기 전으로 바꾸면 끼니는 서버가 지움
        update(access, id, "{\"insulinType\":\"BEDTIME\",\"takenAt\":\"2026-10-21T22:00:00+09:00\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.insulinType").value("BEDTIME"))
                .andExpect(jsonPath("$.data.mealSlot").doesNotExist())
                .andExpect(jsonPath("$.data.units").value(4.5))
                .andExpect(jsonPath("$.data.takenAt").value(Instant.parse("2026-10-21T13:00:00Z").toString()));
        assertThat(jdbc.queryForObject("SELECT meal_slot FROM insulin_logs WHERE id = ?", String.class, id)).isNull();

        // 맞은 시각을 옮기면 목록에서도 옮긴 날짜로 나옴
        list(access, "?from=2026-10-20&to=2026-10-20").andExpect(jsonPath("$.data.items", hasSize(0)));
        list(access, "?from=2026-10-21&to=2026-10-21").andExpect(jsonPath("$.data.items", hasSize(1)));
    }

    @Test
    void 수정할_때도_끼니_규칙을_지킨다() throws Exception {
        long bedtime = createAndGetId(access, json("BEDTIME", null, "10", OCT_20_9AM));
        update(access, bedtime, "{\"insulinType\":\"MEAL\"}")                   // 끼니 없이 식사 때로
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INSULIN_40003"));
        update(access, bedtime, "{\"mealSlot\":\"LUNCH\"}")                     // 자기 전에 끼니
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INSULIN_40004"));
        update(access, bedtime, "{\"insulinType\":\"MEAL\",\"mealSlot\":\"SNACK\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INSULIN_40005"));
        update(access, bedtime, "{\"insulinType\":\"MEAL\",\"mealSlot\":\"LUNCH\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mealSlot").value("LUNCH"));

        // 식사 때 기록은 끼니만 바꿀 수 있고, 다른 칸만 바꾸면 끼니는 그대로
        update(access, bedtime, "{\"mealSlot\":\"DINNER\"}").andExpect(jsonPath("$.data.mealSlot").value("DINNER"));
        update(access, bedtime, "{\"units\":7}").andExpect(jsonPath("$.data.mealSlot").value("DINNER"));
    }

    @Test
    void 값이_실제로_바뀔_때만_editedAt이_찍힌다() throws Exception {
        long id = createAndGetId(access, json("MEAL", "DINNER", "6", OCT_20_9AM));
        list(access, "?from=2026-10-20&to=2026-10-20").andExpect(jsonPath("$.data.items[0].editedAt").doesNotExist());

        update(access, id, "{\"units\":6.0,\"mealSlot\":\"DINNER\"}")   // 같은 값
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editedAt").doesNotExist());
        update(access, id, "{\"units\":7}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editedAt").exists());
        assertThat(jdbc.queryForObject("SELECT edited_at FROM insulin_logs WHERE id = ?", Instant.class, id)).isNotNull();
    }

    // ---------- 지난 기록 불러오기 ----------

    @Test
    void 지난_기록은_같은_종류_같은_끼니_중_가장_늦게_맞은_것() throws Exception {
        create(access, json("MEAL", "DINNER", "5", "2026-10-19T18:00:00+09:00")).andExpect(status().isCreated());
        long dinner = createAndGetId(access, json("MEAL", "DINNER", "6", "2026-10-20T18:00:00+09:00"));
        create(access, json("MEAL", "LUNCH", "4", "2026-10-21T12:00:00+09:00")).andExpect(status().isCreated());   // 끼니 다름
        long bedtime = createAndGetId(access, json("BEDTIME", null, "10", "2026-10-20T22:00:00+09:00"));
        create(other, json("MEAL", "DINNER", "9", "2026-10-21T18:00:00+09:00")).andExpect(status().isCreated());   // 남의 것

        latest(access, "?insulinType=MEAL&mealSlot=DINNER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.insulinLogId").value(dinner))
                .andExpect(jsonPath("$.data.mealSlot").value("DINNER"))
                .andExpect(jsonPath("$.data.units").value(6.0));
        latest(access, "?insulinType=BEDTIME")
                .andExpect(jsonPath("$.data.insulinLogId").value(bedtime));

        // 지우면 그 앞의 기록이 나옴
        delete(access, dinner).andExpect(status().isOk());
        latest(access, "?insulinType=MEAL&mealSlot=DINNER").andExpect(jsonPath("$.data.units").value(5.0));
    }

    @Test
    void 지난_기록이_없으면_data_없이_200() throws Exception {
        latest(access, "?insulinType=MEAL&mealSlot=BREAKFAST")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 지난_기록_조건이_틀리면_400() throws Exception {
        latest(access, "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        latest(access, "?insulinType=CUSTOM").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        latest(access, "?insulinType=MEAL").andExpect(jsonPath("$.errorCode").value("INSULIN_40003"));
        latest(access, "?insulinType=BEDTIME&mealSlot=LUNCH").andExpect(jsonPath("$.errorCode").value("INSULIN_40004"));
        latest(access, "?insulinType=MEAL&mealSlot=SNACK").andExpect(jsonPath("$.errorCode").value("INSULIN_40005"));
    }

    @Test
    void 바꿀_값이_없으면_400() throws Exception {
        long id = createAndGetId(access, json("MEAL", "4", OCT_20_9AM));
        update(access, id, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INSULIN_40002"));
        update(access, id, "{\"units\":null}")   // null은 안 보낸 것과 같음 (비우기 없음)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INSULIN_40002"));
    }

    @Test
    void 수정_값이_범위_밖이면_400이고_기록은_그대로() throws Exception {
        long id = createAndGetId(access, json("MEAL", "4", OCT_20_9AM));
        update(access, id, "{\"units\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        update(access, id, "{\"units\":4.55}").andExpect(status().isBadRequest());
        update(access, id, "{\"units\":1000}").andExpect(status().isBadRequest());
        update(access, id, "{\"insulinType\":\"PUMP\"}").andExpect(status().isBadRequest());
        update(access, id, "{\"takenAt\":\"" + Instant.now().plus(1, ChronoUnit.HOURS) + "\"}")
                .andExpect(status().isBadRequest());
        update(access, id, "{\"takenAt\":\"1999-12-31T23:59:00+09:00\"}").andExpect(status().isBadRequest());

        assertThat((BigDecimal) jdbc.queryForObject("SELECT units FROM insulin_logs WHERE id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("4");
    }

    @Test
    void 남의_기록이나_지운_기록이나_없는_기록은_404() throws Exception {
        long others = createAndGetId(other, json("MEAL", "4", OCT_20_9AM));
        update(access, others, "{\"units\":5}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INSULIN_40401"));

        long deleted = createAndGetId(access, json("MEAL", "4", OCT_20_9AM));
        jdbc.update("UPDATE insulin_logs SET deleted_at = now() WHERE id = ?", deleted);
        update(access, deleted, "{\"units\":5}").andExpect(status().isNotFound());

        update(access, others + 1000, "{\"units\":5}").andExpect(status().isNotFound());
        assertThat((BigDecimal) jdbc.queryForObject("SELECT units FROM insulin_logs WHERE id = ?", BigDecimal.class, others))
                .isEqualByComparingTo("4");
    }

    // ---------- 삭제 ----------

    @Test
    void 지우면_200이고_목록에서_빠지고_줄은_남는다() throws Exception {
        long id = createAndGetId(access, json("MEAL", "4", OCT_20_9AM));
        long kept = createAndGetId(access, json("BEDTIME", "10", "2026-10-20T22:00:00+09:00"));

        delete(access, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        list(access, "?from=2026-10-20&to=2026-10-20")
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].insulinLogId").value(kept));
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM insulin_logs WHERE id = ?", Instant.class, id)).isNotNull();
    }

    @Test
    void 지운_기록은_다시_지우거나_고칠_수_없다() throws Exception {
        long id = createAndGetId(access, json("MEAL", "4", OCT_20_9AM));
        delete(access, id).andExpect(status().isOk());
        delete(access, id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INSULIN_40401"));
        update(access, id, "{\"units\":5}").andExpect(status().isNotFound());
    }

    @Test
    void 남의_기록이나_없는_기록은_지울_수_없다() throws Exception {
        long others = createAndGetId(other, json("MEAL", "4", OCT_20_9AM));
        delete(access, others)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INSULIN_40401"));
        delete(access, others + 1000).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM insulin_logs WHERE id = ?", Instant.class, others)).isNull();
    }

    // ---------- 다른 기기에서 먼저 고친 기록 (#172) ----------

    @Test
    void 화면을_연_뒤_다른_기기에서_먼저_고쳤으면_409이고_덮어쓰지_않는다() throws Exception {
        long id = createAndGetId(access, json("MEAL", "DINNER", "6", OCT_20_9AM));
        jdbc.update("UPDATE insulin_logs SET updated_at = '2026-01-01T00:00:00Z' WHERE id = ?", id);   // 기기 A가 본 값
        String seenByA = "2026-01-01T00:00:00Z";

        // 기기 B가 먼저 고침 (expectedUpdatedAt 없이 → 지금처럼 됨)
        String afterB = JsonPath.read(update(access, id, "{\"units\":4.5}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.updatedAt");

        // 기기 A가 옛 화면 값으로 고치면 409, 단위는 B가 고친 그대로
        update(access, id, "{\"units\":8,\"expectedUpdatedAt\":\"" + seenByA + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INSULIN_40901"));
        assertThat(jdbc.queryForObject("SELECT units FROM insulin_logs WHERE id = ?", java.math.BigDecimal.class, id))
                .isEqualByComparingTo("4.5");

        // 수정 응답의 updatedAt을 다음 수정에 그대로 쓸 수 있음
        update(access, id, "{\"units\":8,\"expectedUpdatedAt\":\"" + afterB + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.units").value(8));
    }

    // ---------- 도우미 ----------

    private ResultActions delete(Cookie who, long id) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.delete("/api/insulin-logs/" + id).cookie(who));
    }

    private ResultActions update(Cookie who, long id, String json) throws Exception {
        return mvc.perform(patch("/api/insulin-logs/" + id).cookie(who).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions list(Cookie who, String query) throws Exception {
        return mvc.perform(get("/api/insulin-logs" + query).cookie(who));
    }

    private long createAndGetId(Cookie who, String json) throws Exception {
        String body = create(who, json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.insulinLogId")).longValue();
    }

    private ResultActions latest(Cookie who, String query) throws Exception {
        return mvc.perform(get("/api/insulin-logs/latest" + query).cookie(who));
    }

    // 끼니를 따로 안 정하는 테스트용: 식사 때면 아침 식사로 채움
    private static String json(String type, String units, String takenAt) {
        return json(type, "MEAL".equals(type) ? "BREAKFAST" : null, units, takenAt);
    }

    // mealSlot이 null이면 칸을 아예 안 보냄
    private static String json(String type, String mealSlot, String units, String takenAt) {
        String slot = mealSlot == null ? "" : ",\"mealSlot\":\"" + mealSlot + "\"";
        return "{\"insulinType\":\"" + type + "\"" + slot + ",\"units\":" + units + ",\"takenAt\":\"" + takenAt + "\"}";
    }

    private ResultActions create(Cookie who, String json) throws Exception {
        return mvc.perform(post("/api/insulin-logs").cookie(who).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Cookie signupAndLogin(String email) throws Exception {
        TestConsents.grantSensitive(jdbc, userRepository.save(User.ofEmail(email, "테스트", passwordEncoder.encode("abcd1234"), Instant.now())));
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }
}
