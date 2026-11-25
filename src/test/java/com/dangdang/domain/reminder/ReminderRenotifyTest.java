package com.dangdang.domain.reminder;

import com.dangdang.domain.push.enums.PushSendResult;
import com.dangdang.domain.reminder.scheduler.ReminderScheduler;
import com.dangdang.support.ReminderTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.sql.Timestamp;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 13:00에 보낸 알림을 두고 시계를 앞으로 옮겨 가며 scheduler.run()을 직접 부른다
class ReminderRenotifyTest extends ReminderTestBase {

    static final Duration NINE = Duration.ofMinutes(9);
    static final Duration TEN = Duration.ofMinutes(10);

    @Autowired ReminderScheduler scheduler;

    Long id;

    @BeforeEach
    void sendOnce() {
        subscribe(jisuId, ENDPOINT);
        id = reminderAt(jisuId, NOW);
        scheduler.run();                      // 13:00 첫 발송
        fakeSender.sent.clear();
    }

    private boolean renotified() {
        return jdbc.queryForObject("SELECT renotified FROM reminders WHERE id = ?", Boolean.class, id);
    }

    private void runAt(Duration afterSent) {
        clock.set(NOW.plus(afterSent));
        scheduler.run();
    }

    @Test
    void 보내고_9분_59초까지는_안_보낸다() {
        runAt(NINE);
        runAt(NINE.plusSeconds(59));

        assertThat(fakeSender.sent).isEmpty();
        assertThat(renotified()).isFalse();
    }

    @Test
    void 보내고_10분이_되면_한_번_더_보낸다() {
        runAt(TEN);

        assertThat(fakeSender.sent).hasSize(1);
        assertThat(fakeSender.sent.get(0).reminderId()).isEqualTo(id);
        assertThat(renotified()).isTrue();
    }

    // 13:00 보냄 → 13:05 식사를 지움 → 13:10 지운 식사에 재알림이 가면 안 됨
    @Test
    void 보낸_뒤_식사를_지우면_재알림을_안_보낸다() {
        Long mealId = jdbc.queryForObject(
                "INSERT INTO meals (user_id, meal_slot, started_at) VALUES (?, 'LUNCH', ?) RETURNING id",
                Long.class, jisuId, Timestamp.from(NOW.minus(Duration.ofHours(1))));
        jdbc.update("UPDATE reminders SET meal_id = ? WHERE id = ?", mealId, id);
        jdbc.update("UPDATE meals SET deleted_at = ? WHERE id = ?", Timestamp.from(NOW.plus(Duration.ofMinutes(5))), mealId);

        runAt(TEN);

        assertThat(fakeSender.sent).isEmpty();
        assertThat(renotified()).isFalse();
        assertThat(statusOf(id)).isEqualTo("SENT");
    }

    @Test
    void 재알림은_한_번뿐이다() {
        runAt(TEN);
        runAt(TEN.plusMinutes(10));
        runAt(TEN.plusMinutes(20));

        assertThat(fakeSender.sent).hasSize(1);
    }

    @Test
    void 재알림도_알림함에_한_줄_더_남기고_상태와_보낸_시각은_그대로() {
        runAt(TEN);

        assertThat(inboxCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT title FROM notifications ORDER BY id DESC LIMIT 1", String.class))
                .isEqualTo("아직 혈당을 안 쟀다면 지금 재 주세요");
        assertThat(statusOf(id)).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("SELECT sent_at FROM reminders WHERE id = ?", Timestamp.class, id).toInstant())
                .isEqualTo(NOW);
    }

    @Test
    void 잠금화면_가리기는_재알림에도_적용된다() {
        runAt(TEN);

        assertThat(fakeSender.sent.get(0).body()).isEqualTo("확인할 일이 있어요");
    }

    @Test
    void 저장을_끝낸_알림은_안_보낸다() {
        setStatus(id, "DONE");

        runAt(TEN);

        assertThat(fakeSender.sent).isEmpty();
        assertThat(renotified()).isFalse();
    }

    @Test
    void 열어_본_알림은_안_보낸다() {
        setStatus(id, "OPENED");

        runAt(TEN);

        assertThat(fakeSender.sent).isEmpty();
    }

    @Test
    void 재알림을_끈_사람은_안_보낸다() {
        jdbc.update("INSERT INTO notification_preferences (user_id, renotify_enabled) VALUES (?, false)", jisuId);

        runAt(TEN);

        assertThat(fakeSender.sent).isEmpty();
        assertThat(renotified()).isFalse();
    }

    @Test
    void 측정_알림을_끈_사람은_안_보낸다() {
        jdbc.update("INSERT INTO notification_preferences (user_id, measurement_enabled) VALUES (?, false)", jisuId);

        runAt(TEN);

        assertThat(fakeSender.sent).isEmpty();
    }

    @Test
    void 한_시간이_넘은_알림은_다시_울리지_않는다() {
        runAt(Duration.ofMinutes(60));

        assertThat(fakeSender.sent).isEmpty();
        assertThat(renotified()).isFalse();
    }

    @Test
    void 보내지_못해도_한_번_시도하면_끝이고_알림함에는_안_남긴다() {
        fakeSender.answers.put(ENDPOINT, PushSendResult.FAILED);

        runAt(TEN);
        runAt(TEN.plusMinutes(1));

        assertThat(fakeSender.sent).hasSize(1);
        assertThat(renotified()).isTrue();
        assertThat(inboxCount()).isEqualTo(1);
    }

    // 첫 알림(13:00) 뒤 13:05에 상태를 바꾸면 13:10 재알림은 안 가고 닫힌다
    private void patchAt5Minutes(String url, String json) throws Exception {
        clock.set(NOW.plus(Duration.ofMinutes(5)));
        mvc.perform(patch(url).cookie(login("jisu@x.com")).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    @Test
    void 보낸_뒤_다른_상황으로_멈추면_재알림이_안_간다() throws Exception {
        patchAt5Minutes("/api/me/measurement-pause", "{\"paused\":true}");

        runAt(TEN);
        runAt(TEN.plusMinutes(1));

        assertThat(fakeSender.sent).isEmpty();
        assertThat(renotified()).isTrue();                     // 닫아서 다시 보지 않는다
        assertThat(inboxCount()).isEqualTo(1);                 // 첫 알림 한 줄만
    }

    @Test
    void 보낸_뒤_입원하면_재알림이_안_간다() throws Exception {
        patchAt5Minutes("/api/me/hospitalization", "{\"hospitalized\":true}");

        runAt(TEN);

        assertThat(fakeSender.sent).isEmpty();
        assertThat(renotified()).isTrue();
    }
}
