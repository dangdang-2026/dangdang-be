package com.dangdang.domain.visit;

import com.dangdang.domain.reminder.scheduler.ReminderScheduler;
import com.dangdang.domain.visit.service.VisitReminderNotifier;
import com.dangdang.support.ReminderTestBase;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 진료 전날·당일 알림 — 시계를 바꿔 가며 스케줄러 메서드를 직접 부름 (기본 시계 = 한국 11/9 13:00)
class VisitReminderTest extends ReminderTestBase {

    static final Instant KST_1859_NOV9 = Instant.parse("2026-11-09T09:59:00Z");
    static final Instant KST_1900_NOV9 = Instant.parse("2026-11-09T10:00:00Z");
    static final Instant KST_2200_NOV9 = Instant.parse("2026-11-09T13:00:00Z");
    static final Instant KST_0659_NOV10 = Instant.parse("2026-11-09T21:59:00Z");
    static final Instant KST_0700_NOV10 = Instant.parse("2026-11-09T22:00:00Z");

    @Autowired ReminderScheduler scheduler;   // 스케줄러에 붙었는지는 첫 테스트에서만 봄
    @Autowired VisitReminderNotifier notifier;  // 나머지는 직접 불러서, 에러를 스케줄러가 삼키지 않게 함

    Cookie jisu;
    long careSiteId;

    @BeforeEach
    void setUp() throws Exception {
        jisu = login("jisu@x.com");
        subscribe(jisuId, ENDPOINT);
        careSiteId = createCareSite(jisu);
    }

    @Test
    void 전날_알림은_저녁_7시부터_한_번만_병원과_시각을_담아_보낸다() throws Exception {
        long visitId = createVisit(jisu, "2026-11-10T10:00:00+09:00", careSiteId);   // 내일 오전 10시

        clock.set(KST_1859_NOV9);
        scheduler.run();
        assertThat(fakeSender.sent).isEmpty();

        clock.set(KST_1900_NOV9);
        scheduler.run();
        scheduler.run();
        assertThat(fakeSender.sent).hasSize(1);
        assertThat(fakeSender.sent.get(0).body()).isEqualTo("확인할 일이 있어요");   // 잠금화면 가리기 기본 켬

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM notifications");
        assertThat(row.get("type")).isEqualTo("VISIT_BEFORE");
        assertThat(row.get("title")).isEqualTo("내일 진료가 있어요");
        assertThat(row.get("body")).isEqualTo("당당산부인과 · 오전 10:00");
        assertThat(row.get("deep_link")).isEqualTo("/visits/" + visitId);
    }

    @Test
    void 밤_10시가_지나_등록하면_전날_알림은_건너뛰고_다음_날_아침_7시에_당일_알림() throws Exception {
        clock.set(KST_2200_NOV9);
        createVisit(jisu, "2026-11-10T10:00:00+09:00", careSiteId);
        notifier.notifyDue();
        assertThat(fakeSender.sent).isEmpty();

        clock.set(KST_0659_NOV10);
        notifier.notifyDue();
        assertThat(fakeSender.sent).isEmpty();

        clock.set(KST_0700_NOV10);
        notifier.notifyDue();
        assertThat(fakeSender.sent).hasSize(1);
        assertThat(types()).containsExactly("VISIT_TODAY");
        assertThat(jdbc.queryForObject("SELECT title FROM notifications", String.class)).isEqualTo("오늘 진료가 있어요");
    }

    @Test
    void 당일_알림은_아직_안_지난_오늘_진료만_보내고_병원이_없으면_시각만() throws Exception {
        createVisit(jisu, "2026-11-09T10:00:00+09:00", null);   // 이미 지난 오전 진료 → 안 보냄
        createVisit(jisu, "2026-11-09T15:00:00+09:00", null);   // 오후 3시 진료 → 보냄

        notifier.notifyDue();   // 13:00

        assertThat(fakeSender.sent).hasSize(1);
        assertThat(types()).containsExactly("VISIT_TODAY");
        assertThat(jdbc.queryForObject("SELECT body FROM notifications", String.class)).isEqualTo("오후 3:00");
    }

    @Test
    void 날짜_미정이나_지운_일정이나_남의_일정은_나에게_안_간다() throws Exception {
        createVisit(jisu, null, careSiteId);
        long deleted = createVisit(jisu, "2026-11-09T15:00:00+09:00", careSiteId);
        mvc.perform(delete("/api/visits/" + deleted).cookie(jisu)).andExpect(status().isOk());
        Cookie minji = login("minji@x.com");
        createVisit(minji, "2026-11-09T16:00:00+09:00", null);   // 민지는 구독이 없어서 푸시는 안 감

        notifier.notifyDue();

        assertThat(fakeSender.sent).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ?", Integer.class, jisuId))
                .isZero();
    }

    @Test
    void 진료_검사_알림을_끈_사람은_안_보내고_다시_보지도_않는다() throws Exception {
        long visitId = createVisit(jisu, "2026-11-09T15:00:00+09:00", careSiteId);
        jdbc.update("INSERT INTO notification_preferences (user_id, schedule_enabled) VALUES (?, false)", jisuId);

        notifier.notifyDue();

        assertThat(fakeSender.sent).isEmpty();
        assertThat(inboxCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT same_day_sent_at FROM visits WHERE id = ?", Instant.class, visitId))
                .isNotNull();
    }

    @Test
    void 진료_날짜를_바꾸면_새_날짜로_다시_보낸다() throws Exception {
        long visitId = createVisit(jisu, "2026-11-10T10:00:00+09:00", careSiteId);
        clock.set(KST_1900_NOV9);
        notifier.notifyDue();
        assertThat(fakeSender.sent).hasSize(1);

        // 병원만 바꾸면 다시 안 보냄
        mvc.perform(patch("/api/visits/" + visitId).cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careSiteId\":null}"))
                .andExpect(status().isOk());
        notifier.notifyDue();
        assertThat(fakeSender.sent).hasSize(1);

        // 같은 시각을 나노초까지 붙여 다시 보내도 (DB에선 같은 값) 다시 안 보냄
        mvc.perform(patch("/api/visits/" + visitId).cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visitAt\":\"2026-11-10T10:00:00.000000001+09:00\"}"))
                .andExpect(status().isOk());
        notifier.notifyDue();
        assertThat(fakeSender.sent).hasSize(1);

        // 같은 날 안에서 시각만 바꾸면 다시 안 보냄
        mvc.perform(patch("/api/visits/" + visitId).cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visitAt\":\"2026-11-10T11:30:00+09:00\"}"))
                .andExpect(status().isOk());
        notifier.notifyDue();
        assertThat(fakeSender.sent).hasSize(1);

        // 다른 날로 옮기면 새 날짜의 전날(11/10 저녁)에 다시 보냄
        mvc.perform(patch("/api/visits/" + visitId).cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visitAt\":\"2026-11-11T09:00:00+09:00\"}"))
                .andExpect(status().isOk());
        clock.set(KST_1900_NOV9.plus(java.time.Duration.ofDays(1)));
        notifier.notifyDue();
        assertThat(fakeSender.sent).hasSize(2);
        assertThat(jdbc.queryForList("SELECT body FROM notifications ORDER BY id", String.class))
                .containsExactly("당당산부인과 · 오전 10:00", "오전 9:00");
    }

    // ---------- 도우미 ----------

    private List<String> types() {
        return jdbc.queryForList("SELECT type FROM notifications ORDER BY id", String.class);
    }

    private long createCareSite(Cookie who) throws Exception {
        String body = mvc.perform(post("/api/care-sites").cookie(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"당당산부인과\",\"department\":\"OBGYN\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.careSiteId")).longValue();
    }

    // visitAt이 null이면 날짜 미정, careSiteId가 null이면 병원 없이
    private long createVisit(Cookie who, String visitAt, Long careSiteId) throws Exception {
        String json = "{" + (visitAt == null ? "" : "\"visitAt\":\"" + visitAt + "\"")
                + (careSiteId == null ? "" : (visitAt == null ? "" : ",") + "\"careSiteId\":" + careSiteId) + "}";
        String body = mvc.perform(post("/api/visits").cookie(who).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.visitId")).longValue();
    }
}
