package com.dangdang.domain.postpartum;

import com.dangdang.domain.reminder.scheduler.ReminderScheduler;
import com.dangdang.support.ReminderTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 입원·다른 상황·출산·되돌리기·산후 검사·졸업 (E-03~E-12). 고정 시계: 한국 2026-11-09 13:00
class PostpartumFlowTest extends ReminderTestBase {

    static final Instant KST_0859_NOV9 = Instant.parse("2026-11-08T23:59:00Z");
    static final Instant KST_0900_NOV9 = Instant.parse("2026-11-09T00:00:00Z");

    @Autowired ReminderScheduler scheduler;

    Cookie jisu;
    Cookie minji;

    @BeforeEach
    void setUp() throws Exception {
        jisu = login("jisu@x.com");
        minji = login("minji@x.com");
        subscribe(jisuId, ENDPOINT);
    }

    private ResultActions patchJson(Cookie who, String url, String json) throws Exception {
        return mvc.perform(patch(url).cookie(who).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions postJson(Cookie who, String url, String json) throws Exception {
        return mvc.perform(post(url).cookie(who).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void deliver(Cookie who, String date) throws Exception {
        patchJson(who, "/api/me/delivery-date", "{\"deliveryDate\":\"" + date + "\"}").andExpect(status().isOk());
    }

    private Long addTest(Cookie who, String name, String date) throws Exception {
        String body = postJson(who, "/api/postpartum/tests", "{\"name\":\"" + name + "\",\"testDate\":\"" + date + "\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private String pregnancyState() {
        return jdbc.queryForObject("SELECT state FROM pregnancies WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                String.class, jisuId);
    }

    // ── 입원 (E-03 · E-04) ──

    @Test
    void 입원하면_예정_알림이_취소되고_새_측정_알림도_안_간다() throws Exception {
        Long before = reminderAt(jisuId, NOW.plusSeconds(600));

        patchJson(jisu, "/api/me/hospitalization", "{\"hospitalized\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("HOSPITALIZED"))
                .andExpect(jsonPath("$.data.pausedReminders").value(1));
        assertThat(statusOf(before)).isEqualTo("CANCELED");

        Long after = reminderAt(jisuId, NOW);          // 입원 중에 식사를 적어 생긴 알림
        scheduler.run();
        assertThat(statusOf(after)).isEqualTo("SKIPPED");
        assertThat(fakeSender.sent).isEmpty();
    }

    @Test
    void 다시_켜면_임신_중으로_돌아가고_알림이_간다() throws Exception {
        patchJson(jisu, "/api/me/hospitalization", "{\"hospitalized\":true}");
        patchJson(jisu, "/api/me/hospitalization", "{\"hospitalized\":false}")
                .andExpect(jsonPath("$.data.state").value("PREGNANT"));

        Long reminder = reminderAt(jisuId, NOW);
        scheduler.run();
        assertThat(statusOf(reminder)).isEqualTo("SENT");
    }

    @Test
    void 입원_중이_아닌데_다시_켜면_409() throws Exception {
        patchJson(jisu, "/api/me/hospitalization", "{\"hospitalized\":false}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PREGNANCY_40901"));
    }

    // ── 다른 상황 (E-03 · E-03b) ──

    @Test
    void 다른_상황이면_알림만_멈추고_다시_켜면_임신_중() throws Exception {
        Long before = reminderAt(jisuId, NOW.plusSeconds(600));

        patchJson(jisu, "/api/me/measurement-pause", "{\"paused\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("PAUSED"))
                .andExpect(jsonPath("$.data.pausedReminders").value(1));
        assertThat(statusOf(before)).isEqualTo("CANCELED");
        Long after = reminderAt(jisuId, NOW);          // 멈춘 동안 식사를 적어 생긴 알림
        scheduler.run();
        assertThat(statusOf(after)).isEqualTo("SKIPPED");
        assertThat(fakeSender.sent).isEmpty();
        mvc.perform(get("/api/me").cookie(jisu)).andExpect(jsonPath("$.data.pregnancyState").value("PAUSED"));

        patchJson(jisu, "/api/me/measurement-pause", "{\"paused\":true}")   // 두 번 눌러도 그대로
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("PAUSED"));
        patchJson(jisu, "/api/me/measurement-pause", "{\"paused\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("PREGNANT"));
        Long resumed = reminderAt(jisuId, NOW);
        scheduler.run();
        assertThat(statusOf(resumed)).isEqualTo("SENT");
    }

    @Test
    void 입원_중에도_멈출_수_있고_멈춘_상태가_아니면_다시_켜기_409() throws Exception {
        patchJson(jisu, "/api/me/measurement-pause", "{\"paused\":false}")   // 임신 정보 없음
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PREGNANCY_40901"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pregnancies", Integer.class)).isZero();

        patchJson(jisu, "/api/me/hospitalization", "{\"hospitalized\":true}").andExpect(status().isOk());
        patchJson(jisu, "/api/me/measurement-pause", "{\"paused\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("PAUSED"));
        assertThat(jdbc.queryForObject("SELECT hospitalized_at IS NULL FROM pregnancies WHERE user_id = ?",
                Boolean.class, jisuId)).isTrue();
        patchJson(jisu, "/api/me/hospitalization", "{\"hospitalized\":false}")   // 입원 중이 아님
                .andExpect(status().isConflict());
    }

    @Test
    void 멈춘_상태에서도_출산일을_적을_수_있고_산후엔_멈추기_409() throws Exception {
        patchJson(jisu, "/api/me/measurement-pause", "{\"paused\":true}").andExpect(status().isOk());
        deliver(jisu, "2026-11-08");
        assertThat(pregnancyState()).isEqualTo("POSTPARTUM");

        patchJson(jisu, "/api/me/measurement-pause", "{\"paused\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PREGNANCY_40901"));
        patchJson(jisu, "/api/me/measurement-pause", "{\"paused\":false}").andExpect(status().isConflict());
    }

    @Test
    void 다른_상황이면_졸업이_안_되고_졸업_뒤엔_멈추기_409() throws Exception {
        patchJson(minji, "/api/me/measurement-pause", "{\"paused\":true}").andExpect(status().isOk());
        mvc.perform(post("/api/me/graduation").cookie(minji))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PREGNANCY_40901"));

        deliver(jisu, "2026-11-08");
        mvc.perform(post("/api/me/graduation").cookie(jisu)).andExpect(status().isOk());
        patchJson(jisu, "/api/me/measurement-pause", "{\"paused\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PREGNANCY_40902"));
        patchJson(jisu, "/api/me/measurement-pause", "{\"paused\":false}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PREGNANCY_40902"));
    }

    @Test
    void 건강정보_동의가_없으면_멈추기와_되돌리기_403() throws Exception {
        deliver(jisu, "2026-11-08");
        jdbc.update("DELETE FROM consent_events WHERE user_id = ?", jisuId);
        patchJson(jisu, "/api/me/measurement-pause", "{\"paused\":true}").andExpect(status().isForbidden());
        mvc.perform(delete("/api/me/delivery-date").cookie(jisu)).andExpect(status().isForbidden());
        assertThat(pregnancyState()).isEqualTo("POSTPARTUM");
    }

    @Test
    void 임신_정보가_없을_때_멈추기가_동시에_와도_한_줄만_생긴다() throws Exception {
        List<Integer> statuses = new ArrayList<>();
        // 테스트가 사람 줄을 먼저 잠가 둠 → 두 요청이 같은 잠금 앞에서 기다리다 차례로 들어간다 (순서가 운에 달리지 않게)
        try (Connection lock = jdbc.getDataSource().getConnection()) {
            lock.setAutoCommit(false);
            try (PreparedStatement ps = lock.prepareStatement("SELECT id FROM users WHERE id = ? FOR UPDATE")) {
                ps.setLong(1, jisuId);
                ps.executeQuery();
            }
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return patchJson(jisu, "/api/me/measurement-pause", "{\"paused\":true}")
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            waitUntilLockWaiters(2);
            lock.rollback();
            for (Future<Integer> result : results) {
                statuses.add(result.get(10, TimeUnit.SECONDS));
            }
            pool.shutdown();
        }
        assertThat(statuses).containsExactly(200, 200);   // ux_pregnancies_active에 걸려 500이 나지 않는다
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pregnancies WHERE user_id = ?", Integer.class, jisuId))
                .isEqualTo(1);
        assertThat(pregnancyState()).isEqualTo("PAUSED");
    }

    // 이 DB에서 잠금을 기다리는 연결이 n개가 될 때까지 (최대 10초)
    private void waitUntilLockWaiters(int n) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = 'Lock'",
                    Integer.class);
            if (waiting != null && waiting >= n) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("두 요청이 잠금을 기다리는 상태가 되지 않았어요");
    }

    // ── 출산 (E-06) ──

    @Test
    void 출산일을_적으면_산후_모드_예정_알림_모두_멈춤() throws Exception {
        reminderAt(jisuId, NOW.plusSeconds(600));
        reminderAt(jisuId, NOW.plusSeconds(1200));

        patchJson(jisu, "/api/me/delivery-date", "{\"deliveryDate\":\"2026-11-08\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("POSTPARTUM"))
                .andExpect(jsonPath("$.data.deliveryDate").value("2026-11-08"))
                .andExpect(jsonPath("$.data.pausedReminders").value(2));
        mvc.perform(get("/api/me").cookie(jisu)).andExpect(jsonPath("$.data.pregnancyState").value("POSTPARTUM"));
    }

    @Test
    void 출산일이_오늘_뒤면_400() throws Exception {
        patchJson(jisu, "/api/me/delivery-date", "{\"deliveryDate\":\"2026-11-10\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PREGNANCY_40001"));
    }

    @Test
    void 산후에는_입원_버튼이_409() throws Exception {
        deliver(jisu, "2026-11-08");
        patchJson(jisu, "/api/me/hospitalization", "{\"hospitalized\":true}").andExpect(status().isConflict());
    }

    // ── 출산 되돌리기 (E-06 · E-07) ──

    @Test
    void 출산을_되돌리면_임신_중이고_출산일과_다시_물어볼_날이_지워진다() throws Exception {
        deliver(jisu, "2026-11-08");
        postJson(jisu, "/api/me/remind-later", "{\"remindOn\":\"2026-11-20\"}").andExpect(status().isOk());
        Long testId = addTest(jisu, "당화혈색소", "2026-12-02");

        mvc.perform(delete("/api/me/delivery-date").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("PREGNANT"))
                .andExpect(jsonPath("$.data.deliveryDate").value(nullValue()))
                .andExpect(jsonPath("$.data.pausedReminders").value(0));
        assertThat(jdbc.queryForObject("SELECT delivery_date IS NULL AND postpartum_ask_on IS NULL"
                + " FROM pregnancies WHERE user_id = ?", Boolean.class, jisuId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM postpartum_tests WHERE id = ?", Integer.class, testId))
                .isEqualTo(1);                                    // 검사 일정은 그대로

        Long reminder = reminderAt(jisuId, NOW);                 // 다시 임신 중 → 측정 알림이 간다
        scheduler.run();
        assertThat(statusOf(reminder)).isEqualTo("SENT");

        mvc.perform(delete("/api/me/delivery-date").cookie(jisu))   // 이미 임신 중
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PREGNANCY_40901"));
    }

    // ── 산후 홈 · 다시 물어보기 (E-07 · E-09 · E-10) ──

    @Test
    void 산후_홈은_출산_며칠째와_다가오는_검사() throws Exception {
        deliver(jisu, "2026-11-01");
        addTest(jisu, "당화혈색소", "2027-01-10");
        addTest(jisu, "75g 경구 당부하 검사", "2026-12-20");
        addTest(jisu, "지난 검사", "2026-11-05");

        mvc.perform(get("/api/postpartum").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dayAfterDelivery").value(9))
                .andExpect(jsonPath("$.data.upcomingTests.length()").value(2))
                .andExpect(jsonPath("$.data.upcomingTests[0].name").value("75g 경구 당부하 검사"))
                .andExpect(jsonPath("$.data.askToday").value(false));
    }

    @Test
    void 다시_물어볼_날이_되면_askToday() throws Exception {
        deliver(jisu, "2026-11-01");
        postJson(jisu, "/api/me/remind-later", "{\"remindOn\":\"2026-11-20\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remindOn").value("2026-11-20"));
        mvc.perform(get("/api/postpartum").cookie(jisu))
                .andExpect(jsonPath("$.data.askOn").value("2026-11-20"))
                .andExpect(jsonPath("$.data.askToday").value(false));

        clock.set(Instant.parse("2026-11-20T00:00:00Z"));   // 한국 11/20 09:00
        mvc.perform(get("/api/postpartum").cookie(jisu)).andExpect(jsonPath("$.data.askToday").value(true));
    }

    @Test
    void 다시_물어볼_날이_지난_날이면_400_산후가_아니면_409() throws Exception {
        postJson(jisu, "/api/me/remind-later", "{\"remindOn\":\"2026-11-20\"}").andExpect(status().isConflict());
        deliver(jisu, "2026-11-01");
        postJson(jisu, "/api/me/remind-later", "{\"remindOn\":\"2026-11-08\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PREGNANCY_40002"));
    }

    // ── 산후 검사 (E-08 · E-11) ──

    @Test
    void 검사_등록_목록_수정_결과_삭제() throws Exception {
        deliver(jisu, "2026-11-01");
        Long id = addTest(jisu, "75g 경구 당부하 검사", "2026-12-20");

        mvc.perform(get("/api/postpartum/tests").cookie(jisu))
                .andExpect(jsonPath("$.data[0].remindDayBefore").value(true))
                .andExpect(jsonPath("$.data[0].remindSameDay").value(true))
                .andExpect(jsonPath("$.data[0].resultNote").value(nullValue()));

        patchJson(jisu, "/api/postpartum/tests/" + id, "{\"testDate\":\"2026-12-27\",\"remindSameDay\":false}")
                .andExpect(jsonPath("$.data.testDate").value("2026-12-27"))
                .andExpect(jsonPath("$.data.remindSameDay").value(false))
                .andExpect(jsonPath("$.data.name").value("75g 경구 당부하 검사"));

        patchJson(jisu, "/api/postpartum/tests/" + id + "/result", "{\"resultNote\":\"정상이라고 하셨어요.\"}")
                .andExpect(jsonPath("$.data.resultNote").value("정상이라고 하셨어요."));
        patchJson(jisu, "/api/postpartum/tests/" + id + "/result", "{\"resultNote\":\" \"}")
                .andExpect(jsonPath("$.data.resultNote").value(nullValue()));

        mvc.perform(delete("/api/postpartum/tests/" + id).cookie(jisu)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM postpartum_tests", Integer.class)).isZero();
    }

    @Test
    void 산후가_아니면_검사_등록_409_남의_검사는_404() throws Exception {
        postJson(jisu, "/api/postpartum/tests", "{\"name\":\"검사\",\"testDate\":\"2026-12-20\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("POSTPARTUM_40901"));
        deliver(jisu, "2026-11-01");
        Long id = addTest(jisu, "검사", "2026-12-20");
        patchJson(minji, "/api/postpartum/tests/" + id + "/result", "{\"resultNote\":\"남의 것\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("POSTPARTUM_40401"));
    }

    @Test
    void 이름이_비거나_40자_넘으면_400() throws Exception {
        deliver(jisu, "2026-11-01");
        postJson(jisu, "/api/postpartum/tests", "{\"name\":\" \",\"testDate\":\"2026-12-20\"}")
                .andExpect(status().isBadRequest());
        postJson(jisu, "/api/postpartum/tests", "{\"name\":\"" + "가".repeat(41) + "\",\"testDate\":\"2026-12-20\"}")
                .andExpect(status().isBadRequest());
    }

    // ── 검사 알림 (전날·당일, 오전 9시부터) ──

    @Test
    void 전날_알림은_오전_9시부터_한_번만() throws Exception {
        deliver(jisu, "2026-11-01");
        addTest(jisu, "75g 경구 당부하 검사", "2026-11-10");     // 내일

        clock.set(KST_0859_NOV9);
        scheduler.run();
        assertThat(fakeSender.sent).isEmpty();

        clock.set(KST_0900_NOV9);
        scheduler.run();
        scheduler.run();
        assertThat(fakeSender.sent).hasSize(1);
        assertThat(fakeSender.sent.get(0).body()).isEqualTo("확인할 일이 있어요");   // 잠금화면 가리기 기본 켬
        assertThat(jdbc.queryForObject("SELECT title FROM notifications WHERE type = 'POSTPARTUM'", String.class))
                .isEqualTo("내일 75g 경구 당부하 검사 검사가 있어요");
    }

    @Test
    void 당일_알림() throws Exception {
        deliver(jisu, "2026-11-01");
        addTest(jisu, "당화혈색소", "2026-11-09");                // 오늘
        scheduler.run();                                         // 13:00
        assertThat(jdbc.queryForObject("SELECT title FROM notifications WHERE type = 'POSTPARTUM'", String.class))
                .isEqualTo("오늘 당화혈색소 검사가 있어요");
    }

    @Test
    void 진료_검사_알림을_끄면_안_보내고_다시_보지도_않는다() throws Exception {
        deliver(jisu, "2026-11-01");
        addTest(jisu, "당화혈색소", "2026-11-09");
        patchJson(jisu, "/api/me/notification-preferences", "{\"scheduleEnabled\":false}").andExpect(status().isOk());

        scheduler.run();
        assertThat(fakeSender.sent).isEmpty();
        assertThat(jdbc.queryForObject("SELECT same_day_sent_at IS NOT NULL FROM postpartum_tests", Boolean.class))
                .isTrue();
    }

    @Test
    void 한_사람_발송이_터져도_다른_사람은_받고_같은_알림이_다시_가지_않는다() throws Exception {
        subscribe(minjiId, ENDPOINT_B);
        fakeSender.explodes.add(ENDPOINT_B);                     // 민지 기기만 푸시 서버 오류
        deliver(minji, "2026-11-01");
        deliver(jisu, "2026-11-01");
        addTest(minji, "당화혈색소", "2026-11-09");
        addTest(jisu, "당화혈색소", "2026-11-09");

        scheduler.run();
        scheduler.run();                                         // 30초 뒤 한 바퀴 더

        assertThat(fakeSender.sent).hasSize(1);                  // 지수만, 한 번만
        assertThat(jdbc.queryForObject("SELECT count(*) FROM postpartum_tests WHERE same_day_sent_at IS NULL",
                Integer.class)).isZero();                        // 둘 다 처리됨 (되돌아가지 않음)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE type = 'POSTPARTUM'", Integer.class))
                .isEqualTo(2);                                   // 알림함에는 둘 다
    }

    @Test
    void 날짜를_바꾸면_알림을_다시_보낸다() throws Exception {
        deliver(jisu, "2026-11-01");
        Long id = addTest(jisu, "당화혈색소", "2026-11-09");
        scheduler.run();
        assertThat(fakeSender.sent).hasSize(1);

        patchJson(jisu, "/api/postpartum/tests/" + id, "{\"testDate\":\"2026-11-10\"}");
        scheduler.run();                                         // 이번엔 내일 검사 → 전날 알림
        assertThat(fakeSender.sent).hasSize(2);
    }

    // ── 졸업 (E-12) ──

    @Test
    void 졸업하면_지킨_측정_수와_끝난_임신() throws Exception {
        postJson(jisu, "/api/readings", "{\"value\":92,\"measuredAt\":\"2026-11-01T07:00:00+09:00\",\"readingType\":\"FASTING\"}");
        postJson(jisu, "/api/readings", "{\"value\":120,\"measuredAt\":\"2026-11-02T13:00:00+09:00\",\"readingType\":\"POST_MEAL\"}");
        deliver(jisu, "2026-11-08");

        mvc.perform(post("/api/me/graduation").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.keptMeasurements").value(2))
                .andExpect(jsonPath("$.data.weeksTogether").value(1));
        assertThat(pregnancyState()).isEqualTo("ENDED");
        mvc.perform(get("/api/me").cookie(jisu)).andExpect(jsonPath("$.data.pregnancyState").value(nullValue()));

        patchJson(jisu, "/api/me/delivery-date", "{\"deliveryDate\":\"2026-11-08\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PREGNANCY_40902"));

        Long reminder = reminderAt(jisuId, NOW);                 // 졸업한 사람에겐 측정 알림이 안 간다
        scheduler.run();
        assertThat(statusOf(reminder)).isEqualTo("SKIPPED");
    }

    @Test
    void 함께한_날은_가입한_한국_날짜가_1일째() throws Exception {
        // 한국 8/6 23:30 가입 (UTC로는 8/6 14:30) → 오늘 11/9 13:00까지 96일째. 가입 시각을 고정해 실제 시계와 상관없게
        jdbc.update("UPDATE users SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-08-06T14:30:00Z")), jisuId);
        deliver(jisu, "2026-11-08");

        mvc.perform(post("/api/me/graduation").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("임신 중 관리를 마쳤어요."))
                .andExpect(jsonPath("$.data.daysTogether").value(96))
                .andExpect(jsonPath("$.data.weeksTogether").value(13));
    }

    @Test
    void 졸업은_산후에서만() throws Exception {
        mvc.perform(post("/api/me/graduation").cookie(jisu))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PREGNANCY_40901"));
    }
}
