package com.dangdang.domain.user;

import com.dangdang.support.ReminderTestBase;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 건강정보 동의 철회 (M-07b): 건강정보만 바로 파기, 계정·남의 기록은 그대로, 다음엔 A-01부터
class SensitiveConsentWithdrawalFlowTest extends ReminderTestBase {

    static final String A01 = """
            {"items":[
              {"type":"AGE_14","granted":true},{"type":"TERMS","granted":true},
              {"type":"PRIVACY","granted":true},{"type":"SENSITIVE","granted":true},
              {"type":"AI_CHAT_RECORDS","granted":true}]}
            """;
    static final String READING = "{\"value\":92,\"measuredAt\":\"2026-11-09T07:00:00+09:00\",\"readingType\":\"FASTING\"}";

    // 이 표들에 user_id가 있고, 철회하면 0이 돼야 한다
    static final List<String> HEALTH_TABLES = List.of(
            "readings", "meals", "criteria_versions", "reminders", "pregnancies",
            "visits", "care_sites", "chat_messages");

    Cookie jisu;
    Cookie minji;

    @BeforeEach
    void setUp() throws Exception {
        jisu = login("jisu@x.com");
        minji = login("minji@x.com");
        fill(jisu, jisuId);
        fill(minji, minjiId);
    }

    // 동의 + 건강정보를 골고루 넣는다 (지운 혈당도 하나)
    private void fill(Cookie who, Long userId) throws Exception {
        send(post("/api/me/consents"), who, A01).andExpect(status().isOk());
        send(post("/api/criteria-versions"), who,
                "{\"postMealMinutes\":120,\"measureSlots\":[\"LUNCH_AFTER\"],\"effectiveFrom\":\"2026-11-01\"}")
                .andExpect(status().isCreated());
        send(post("/api/meals"), who,   // 식후 알림도 하나 예약됨
                "{\"mealSlot\":\"LUNCH\",\"startedAt\":\"" + NOW.minus(Duration.ofMinutes(10)) + "\"}")
                .andExpect(status().isCreated());
        long deleted = id(send(post("/api/readings"), who, READING).andExpect(status().isCreated()), "$.data.readingId");
        mvc.perform(delete("/api/readings/" + deleted).cookie(who)).andExpect(status().isOk());
        send(post("/api/readings"), who, READING).andExpect(status().isCreated());
        send(patch("/api/me/due-date"), who, "{\"dueDate\":\"2027-03-01\"}").andExpect(status().isOk());
        long site = id(send(post("/api/care-sites"), who, "{\"name\":\"한빛산부인과\",\"department\":\"OBGYN\"}")
                .andExpect(status().isCreated()), "$.data.careSiteId");
        long visit = id(send(post("/api/visits"), who,
                "{\"careSiteId\":" + site + ",\"visitAt\":\"2026-11-14T10:30:00+09:00\"}")
                .andExpect(status().isCreated()), "$.data.visitId");
        send(post("/api/visits/" + visit + "/notes"), who, "{\"content\":\"공복 95 미만\"}").andExpect(status().isCreated());
        send(patch("/api/me/notification-preferences"), who, "{\"measurementEnabled\":true}").andExpect(status().isOk());
        jdbc.update("INSERT INTO chat_messages (user_id, role, kind, content) VALUES (?, 'USER', 'QUESTION', '공복 98이면 괜찮아?')", userId);
    }

    @Test
    void 철회하면_건강정보가_지운_기록까지_사라지고_계정과_남의_기록은_그대로() throws Exception {
        mvc.perform(post("/api/me/consents/sensitive/withdraw").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(false))
                .andExpect(jsonPath("$.data.items[?(@.type=='SENSITIVE')].granted").value(false))
                .andExpect(jsonPath("$.data.items[?(@.type=='AI_CHAT_RECORDS')].granted").value(false));

        for (String table : HEALTH_TABLES) {
            assertThat(count(table, jisuId)).as(table).isZero();
            assertThat(count(table, minjiId)).as(table).isPositive();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM visit_notes", Integer.class)).isEqualTo(1);   // 민지 것만
        assertThat(jdbc.queryForObject(
                "SELECT measurement_enabled FROM notification_preferences WHERE user_id = ?", Boolean.class, jisuId)).isFalse();
        // 계정은 남아서 로그인되고, 다음에 열면 A-01로
        mvc.perform(get("/api/me").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consentCompleted").value(false))
                .andExpect(jsonPath("$.data.dueDate").doesNotExist());
    }

    @Test
    void 두_번_불러도_200이고_철회_이력은_한_줄() throws Exception {
        mvc.perform(post("/api/me/consents/sensitive/withdraw").cookie(jisu)).andExpect(status().isOk());
        mvc.perform(post("/api/me/consents/sensitive/withdraw").cookie(jisu)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM consent_events WHERE user_id = ? AND consent_type = 'SENSITIVE' AND granted = false",
                Integer.class, jisuId)).isEqualTo(1);
    }

    @Test
    void 철회_뒤_A01에서_다시_동의하면_처음처럼_쓸_수_있다() throws Exception {
        mvc.perform(post("/api/me/consents/sensitive/withdraw").cookie(jisu)).andExpect(status().isOk());
        send(post("/api/me/consents"), jisu, A01)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true));
        send(post("/api/readings"), jisu, READING).andExpect(status().isCreated());
    }

    @Test
    void 일반_동의_저장으로_SENSITIVE를_끄는_건_여전히_400() throws Exception {
        send(post("/api/me/consents"), jisu, "{\"items\":[{\"type\":\"SENSITIVE\",\"granted\":false}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("USER_40001"));
        assertThat(count("readings", jisuId)).isPositive();   // 아무것도 안 지워짐
    }

    // ── 문서 버전과 맞물림 (78 E) ──
    @Test
    void 예전_버전으로_동의했어도_철회하면_철회_이력이_남는다() throws Exception {
        jdbc.update("UPDATE consent_events SET document_version = 'v0' WHERE user_id = ? AND consent_type = 'SENSITIVE'", jisuId);
        mvc.perform(post("/api/me/consents/sensitive/withdraw").cookie(jisu)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM consent_events WHERE user_id = ? AND consent_type = 'SENSITIVE' AND granted = false",
                Integer.class, jisuId)).isEqualTo(1);
    }

    private int count(String table, Long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
    }

    private ResultActions send(MockHttpServletRequestBuilder req, Cookie who, String body) throws Exception {
        return mvc.perform(req.cookie(who).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long id(ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
    }
}
