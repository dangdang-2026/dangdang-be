package com.dangdang.domain.reminder;

import com.dangdang.domain.reminder.scheduler.ReminderScheduler;
import com.dangdang.domain.user.entity.NotificationPreference;
import com.dangdang.domain.user.repository.NotificationPreferenceRepository;
import com.dangdang.support.ReminderTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 공복·자기 전 측정 알림 (A-02d·M-19) + 알림을 열면 알림함도 읽음 (H-15)
class DailyReminderFlowTest extends ReminderTestBase {

    // 한국 2026-11-09 07:00 = UTC 2026-11-08 22:00
    static final Instant FASTING_7AM = Instant.parse("2026-11-08T22:00:00Z");
    static final Instant BEDTIME_10PM = Instant.parse("2026-11-09T13:00:00Z");

    @Autowired ReminderScheduler scheduler;
    @Autowired NotificationPreferenceRepository notificationPreferenceRepository;

    Cookie jisu;

    @BeforeEach
    void setUp() throws Exception {
        jisu = login("jisu@x.com");
        subscribe(jisuId, ENDPOINT);
        preference(jisuId);                             // 공복 07:00 · 자기 전 22:00 (기본값)
        criteria(jisuId, "[\"FASTING\", \"LUNCH_AFTER\", \"BEDTIME\"]");
    }

    // 알림 시각(TIME)은 SQL로 직접 넣지 않는다 — application.yaml의 jdbc.time_zone(UTC) 때문에
    //  맥(JVM 한국 시간)에서는 앱이 읽을 때 9시간 밀린다. 가입할 때처럼 앱(JPA)으로 저장한다
    private void preference(Long userId) {
        notificationPreferenceRepository.save(
                NotificationPreference.defaultsFor(userRepository.findById(userId).orElseThrow()));
    }

    private void criteria(Long userId, String slotsJson) {
        jdbc.update("INSERT INTO criteria_versions (user_id, measure_slots, effective_from) VALUES (?, ?::jsonb, ?)",
                userId, slotsJson, LocalDate.of(2026, 11, 1));
    }

    private void pregnancy(Long userId, String state) {
        jdbc.update("INSERT INTO pregnancies (user_id, due_date, state, created_at, updated_at)"
                + " VALUES (?, '2026-12-20', ?, now(), now())", userId, state);
    }

    private List<Map<String, Object>> reminders() {
        return jdbc.queryForList("SELECT slot, status, meal_id, scheduled_at FROM reminders ORDER BY id");
    }

    @Test
    void 공복_시각이_되면_알림이_가고_알림함에_남는다() throws Exception {
        clock.set(FASTING_7AM);
        scheduler.run();

        assertThat(reminders()).hasSize(1);
        assertThat(reminders().get(0)).containsEntry("slot", "FASTING").containsEntry("status", "SENT");
        assertThat(reminders().get(0).get("meal_id")).isNull();
        assertThat(fakeSender.sent).hasSize(1);
        mvc.perform(get("/api/notifications").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].title").value("공복 혈당을 잴 시간이에요"))
                .andExpect(jsonPath("$.data.items[0].body").value(nullValue()));   // 식사가 없는 알림은 내용 없음 (33번)
    }

    @Test
    void 시각_전에는_만들지_않는다() {
        clock.set(FASTING_7AM.minusSeconds(60));
        scheduler.run();
        assertThat(reminders()).isEmpty();
    }

    @Test
    void 삼십초마다_돌아도_하루에_한_번만() {
        clock.set(FASTING_7AM);
        scheduler.run();
        clock.set(FASTING_7AM.plusSeconds(30));
        scheduler.run();
        assertThat(reminders()).hasSize(1);
        assertThat(fakeSender.sent).hasSize(1);
    }

    @Test
    void 삼십분_넘게_지났으면_오늘은_건너뛴다() {
        clock.set(FASTING_7AM.plusSeconds(31 * 60));   // 서버가 꺼져 있다가 07:31에 켜짐
        scheduler.run();
        assertThat(reminders()).isEmpty();
    }

    @Test
    void 병원_기준에_공복이_없으면_만들지_않는다() {
        jdbc.update("DELETE FROM criteria_versions");
        criteria(jisuId, "[\"LUNCH_AFTER\"]");
        clock.set(FASTING_7AM);
        scheduler.run();
        assertThat(reminders()).isEmpty();
    }

    @Test
    void 병원_기준이_없으면_만들지_않는다() {
        jdbc.update("DELETE FROM criteria_versions");
        clock.set(FASTING_7AM);
        scheduler.run();
        assertThat(reminders()).isEmpty();
    }

    @Test
    void 측정_알림을_끄면_만들지_않는다() {
        jdbc.update("UPDATE notification_preferences SET measurement_enabled = false");
        clock.set(FASTING_7AM);
        scheduler.run();
        assertThat(reminders()).isEmpty();
    }

    @Test
    void 입원_중이거나_출산_뒤_졸업했으면_만들지_않는다() {
        pregnancy(jisuId, "HOSPITALIZED");
        clock.set(FASTING_7AM);
        scheduler.run();
        assertThat(reminders()).isEmpty();

        jdbc.update("UPDATE pregnancies SET state = 'ENDED'");   // 졸업: 진행 중인 임신이 없고 끝난 임신만 있음
        scheduler.run();
        assertThat(reminders()).isEmpty();
    }

    @Test
    void 임신_중이면_만든다() {
        pregnancy(jisuId, "PREGNANT");
        clock.set(FASTING_7AM);
        scheduler.run();
        assertThat(reminders()).hasSize(1);
    }

    @Test
    void 공복_시각을_바꾸면_바꾼_시각에_온다() throws Exception {
        mvc.perform(patch("/api/me/notification-preferences").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fastingAlarmTime\":\"06:45\"}"))
                .andExpect(status().isOk());
        clock.set(FASTING_7AM);                          // 07:00 — 06:45 알림을 30분 안이라 늦게라도 만든다
        scheduler.run();
        assertThat(reminders()).hasSize(1);
        assertThat(((java.sql.Timestamp) reminders().get(0).get("scheduled_at")).toInstant())
                .isEqualTo(FASTING_7AM.minusSeconds(15 * 60));   // 한국 06:45
    }

    @Test
    void 자기_전_알림도_같은_방식() {
        clock.set(BEDTIME_10PM);
        scheduler.run();
        assertThat(reminders()).hasSize(1);
        assertThat(reminders().get(0)).containsEntry("slot", "BEDTIME");
    }

    @Test
    void 공복_혈당을_저장하면_알림이_끝나고_재알림이_안_간다() throws Exception {
        clock.set(FASTING_7AM);
        scheduler.run();
        assertThat(fakeSender.sent).hasSize(1);

        clock.set(FASTING_7AM.plusSeconds(5 * 60));
        mvc.perform(post("/api/readings").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":92,\"measuredAt\":\"2026-11-09T07:04:00+09:00\","
                                + "\"readingType\":\"FASTING\",\"slot\":\"FASTING\"}"))
                .andExpect(status().isCreated());
        assertThat(reminders().get(0)).containsEntry("status", "DONE");

        clock.set(FASTING_7AM.plusSeconds(11 * 60));    // 보낸 지 10분이 지나도
        scheduler.run();
        assertThat(fakeSender.sent).hasSize(1);          // 재알림이 안 간다
    }

    @Test
    void 다른_날_공복_혈당은_오늘_알림을_끝내지_않는다() throws Exception {
        clock.set(FASTING_7AM);
        scheduler.run();

        mvc.perform(post("/api/readings").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":92,\"measuredAt\":\"2026-11-08T07:04:00+09:00\","
                                + "\"readingType\":\"FASTING\",\"slot\":\"FASTING\"}"))
                .andExpect(status().isCreated());
        assertThat(reminders().get(0)).containsEntry("status", "SENT");
    }

    @Test
    void 알림을_열면_알림함의_같은_알림도_읽음() throws Exception {
        clock.set(FASTING_7AM);
        scheduler.run();
        clock.set(FASTING_7AM.plusSeconds(11 * 60));
        scheduler.run();                                  // 재알림 → 알림함에 2줄
        assertThat(inboxCount()).isEqualTo(2);
        Long reminderId = jdbc.queryForObject("SELECT id FROM reminders", Long.class);

        mvc.perform(post("/api/reminders/" + reminderId + "/open").cookie(jisu))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE read_at IS NULL", Integer.class))
                .isZero();
    }

    @Test
    void 남의_알림함은_건드리지_않는다() throws Exception {
        Long mine = reminderAt(jisuId, NOW);
        Long hers = reminderAt(minjiId, NOW);
        subscribe(minjiId, ENDPOINT_B);
        clock.set(NOW);
        scheduler.run();

        mvc.perform(post("/api/reminders/" + mine + "/open").cookie(jisu)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE reminder_id = ? AND read_at IS NULL",
                Integer.class, hers)).isEqualTo(1);
    }
}
