package com.dangdang.domain.user;

import com.dangdang.domain.user.service.SensitiveConsentWithdrawalService;
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
import java.util.Set;

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

    // user_id가 있지만 철회해도 남기는 표 — 건강정보가 아니거나(계정·커뮤니티·설정·기기·상점), 따로 정한 것(내 음식)
    // 상점 찜·판매처로 간 기록·최근 본 상품은 건강정보가 아니라 남긴다 (탈퇴하면 CASCADE로 지워짐, 화면에서 직접 지울 수 있음)
    // 새 표를 만들면 철회 목록(ERASED_TABLES)이나 여기 중 하나에 넣어야 아래 테스트가 통과한다
    static final Set<String> KEPT_TABLES = Set.of(
            "consent_events", "notification_preferences", "push_subscriptions", "meal_settings", "my_foods",
            "community_profiles", "user_sanctions", "sanction_appeals", "card_reactions", "card_saves",
            "product_likes", "product_visits", "product_views");

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
        long meal = id(send(post("/api/meals"), who,   // 식후 알림도 하나 예약됨
                "{\"mealSlot\":\"LUNCH\",\"startedAt\":\"" + NOW.minus(Duration.ofMinutes(10)) + "\"}")
                .andExpect(status().isCreated()), "$.data.mealId");
        send(post("/api/meals/" + meal + "/items"), who, "{\"name\":\"현미밥\"}").andExpect(status().isCreated());
        long deleted = id(send(post("/api/readings"), who, READING).andExpect(status().isCreated()), "$.data.readingId");
        mvc.perform(delete("/api/readings/" + deleted).cookie(who)).andExpect(status().isOk());
        send(post("/api/readings"), who, READING).andExpect(status().isCreated());
        send(post("/api/insulin-logs"), who, "{\"insulinType\":\"BEDTIME\",\"units\":6,\"takenAt\":\"" + NOW.minus(Duration.ofMinutes(5)) + "\"}")
                .andExpect(status().isCreated());
        send(patch("/api/me/due-date"), who, "{\"dueDate\":\"2027-03-01\"}").andExpect(status().isOk());
        long site = id(send(post("/api/care-sites"), who, "{\"name\":\"한빛산부인과\",\"department\":\"OBGYN\"}")
                .andExpect(status().isCreated()), "$.data.careSiteId");
        long visit = id(send(post("/api/visits"), who,
                "{\"careSiteId\":" + site + ",\"visitAt\":\"2026-11-14T10:30:00+09:00\"}")
                .andExpect(status().isCreated()), "$.data.visitId");
        send(post("/api/visits/" + visit + "/notes"), who, "{\"content\":\"공복 95 미만\"}").andExpect(status().isCreated());
        send(post("/api/visits/" + visit + "/questions"), who, "{\"content\":\"식후 1시간에 재도 되나요?\"}")
                .andExpect(status().isCreated());
        send(post("/api/visit-reports"), who, "{\"kind\":\"VISIT\",\"visitId\":" + visit + "}")
                .andExpect(status().isCreated());
        send(put("/api/me/meal-plan"), who, "{\"mealGuide\":\"아침: 잡곡밥 2/3공기\"}").andExpect(status().isOk());
        send(patch("/api/me/delivery-date"), who, "{\"deliveryDate\":\"2026-11-08\"}").andExpect(status().isOk());
        send(post("/api/postpartum/tests"), who, "{\"name\":\"75g 검사\",\"testDate\":\"2026-12-20\"}")
                .andExpect(status().isCreated());
        // 알림함: 건강 알림 하나 + 건강이 아닌 종류 하나 (지금 enum에는 없어서 DB에 바로 넣는다 — 이건 남아야 한다)
        jdbc.update("INSERT INTO notifications (user_id, type, title, deep_link, sent_at) VALUES (?, 'MEASURE', '혈당 잴 시간', '/', now())", userId);
        jdbc.update("INSERT INTO notifications (user_id, type, title, deep_link, sent_at) VALUES (?, 'NOT_HEALTH', '다른 알림', '/', now())", userId);
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

        for (String table : SensitiveConsentWithdrawalService.ERASED_TABLES) {
            if (table.equals("notifications")) {
                continue;   // 건강 알림만 지움 — 아래에서 따로
            }
            assertThat(count(table, jisuId)).as(table).isZero();
            assertThat(count(table, minjiId)).as(table).isPositive();
        }
        assertThat(jdbc.queryForList("SELECT type FROM notifications WHERE user_id = ?", String.class, jisuId))
                .containsExactly("NOT_HEALTH");
        assertThat(count("notifications", minjiId)).isEqualTo(2);
        // user_id 없이 부모 따라 지워지는 표 (ON DELETE CASCADE) — 민지 것만 남는다
        for (String table : List.of("meal_items", "visit_notes", "visit_questions")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)).as(table).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject(
                "SELECT measurement_enabled FROM notification_preferences WHERE user_id = ?", Boolean.class, jisuId)).isFalse();
        // 계정은 남아서 로그인되고, 다음에 열면 A-01로
        mvc.perform(get("/api/me").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consentCompleted").value(false))
                .andExpect(jsonPath("$.data.dueDate").doesNotExist());
    }

    @Test
    void user_id가_있는_표는_모두_철회_목록이나_남기는_목록에_있다() {
        // 새 건강 기록 표를 만들고 철회 목록에 안 넣으면 여기서 걸린다 (철회해도 몰래 남는 일 막기)
        List<String> tables = jdbc.queryForList(
                "SELECT DISTINCT table_name FROM information_schema.columns"
                        + " WHERE table_schema = current_schema() AND column_name = 'user_id'", String.class);
        for (String table : tables) {
            assertThat(SensitiveConsentWithdrawalService.ERASED_TABLES.contains(table) || KEPT_TABLES.contains(table))
                    .as(table + " 를 철회 때 지울지(ERASED_TABLES) 남길지(KEPT_TABLES) 정해 주세요").isTrue();
        }
        assertThat(tables).containsAll(SensitiveConsentWithdrawalService.ERASED_TABLES).containsAll(KEPT_TABLES);
    }

    // ── 철회 뒤 저장 막기 ──
    @Test
    void 철회_뒤엔_혈당을_저장하면_403이고_다시_동의하기_전까지_안_남는다() throws Exception {
        mvc.perform(post("/api/me/consents/sensitive/withdraw").cookie(jisu)).andExpect(status().isOk());
        send(post("/api/readings"), jisu, READING)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("USER_40302"));
        send(patch("/api/me/due-date"), jisu, "{\"dueDate\":\"2027-03-01\"}")
                .andExpect(status().isForbidden());
        assertThat(count("readings", jisuId)).isZero();
        assertThat(count("pregnancies", jisuId)).isZero();
    }

    @Test
    void 건강정보_동의가_없으면_식사도_403() throws Exception {
        jdbc.update("DELETE FROM consent_events WHERE user_id = ? AND consent_type = 'SENSITIVE'", jisuId);
        send(post("/api/meals"), jisu, "{\"mealSlot\":\"DINNER\",\"startedAt\":\"" + NOW + "\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("USER_40302"));
    }

    @Test
    void 철회_뒤_기록_사용_동의만_다시_켰어도_또_철회하면_꺼진다() throws Exception {
        mvc.perform(post("/api/me/consents/sensitive/withdraw").cookie(jisu)).andExpect(status().isOk());
        send(post("/api/me/consents"), jisu, "{\"items\":[{\"type\":\"AI_CHAT_RECORDS\",\"granted\":true}]}")
                .andExpect(status().isOk());
        mvc.perform(post("/api/me/consents/sensitive/withdraw").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.type=='AI_CHAT_RECORDS')].granted").value(false));
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
