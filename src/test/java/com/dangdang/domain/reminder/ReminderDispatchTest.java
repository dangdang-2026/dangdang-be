package com.dangdang.domain.reminder;

import com.dangdang.domain.push.enums.PushSendResult;
import com.dangdang.domain.reminder.scheduler.ReminderScheduler;
import com.dangdang.global.config.SchedulingConfig;
import com.dangdang.support.ReminderTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// 시계를 13:00에 고정하고 스케줄러 메서드를 직접 부른다
class ReminderDispatchTest extends ReminderTestBase {

    @Autowired ReminderScheduler scheduler;
    @Autowired ApplicationContext context;

    @Test
    void 테스트에서는_스케줄러가_혼자_돌지_않는다() {
        assertThat(context.getBeansOfType(SchedulingConfig.class)).isEmpty();
    }

    @Test
    void 시간이_된_예약만_보내고_안_된_예약은_그대로() {
        subscribe(jisuId, ENDPOINT);
        Long due = reminderAt(jisuId, NOW.minus(Duration.ofMinutes(1)));
        Long exact = reminderAt(jisuId, NOW);
        Long later = reminderAt(jisuId, NOW.plus(Duration.ofMinutes(1)));

        scheduler.run();

        assertThat(statusOf(due)).isEqualTo("SENT");
        assertThat(statusOf(exact)).isEqualTo("SENT");
        assertThat(statusOf(later)).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("SELECT sent_at FROM reminders WHERE id = ?", Timestamp.class, due).toInstant())
                .isEqualTo(NOW);
    }

    @Test
    void 보내면_알림함에_한_줄이_생긴다() {
        subscribe(jisuId, ENDPOINT);
        Long id = reminderAt(jisuId, NOW);

        scheduler.run();

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM notifications");
        assertThat(row.get("user_id")).isEqualTo(jisuId);
        assertThat(row.get("reminder_id")).isEqualTo(id);
        assertThat(row.get("type")).isEqualTo("MEASURE");
        assertThat(row.get("title")).isEqualTo("혈당 잴 시간이에요");
        assertThat(row.get("body")).isEqualTo("점심 식후 혈당을 재 주세요");
        assertThat(row.get("deep_link")).isEqualTo("/today?sheet=glucose&reminderId=" + id);
        assertThat(((Timestamp) row.get("sent_at")).toInstant()).isEqualTo(NOW);
        assertThat(row.get("read_at")).isNull();
    }

    @Test
    void 기기가_두_대여도_알림함은_한_줄() {
        subscribe(jisuId, ENDPOINT);
        subscribe(jisuId, ENDPOINT_B);
        reminderAt(jisuId, NOW);

        scheduler.run();

        assertThat(fakeSender.sent).hasSize(2);
        assertThat(inboxCount()).isEqualTo(1);
    }

    @Test
    void 두_번_돌아도_한_번만_보낸다() {
        subscribe(jisuId, ENDPOINT);
        reminderAt(jisuId, NOW);

        scheduler.run();
        scheduler.run();

        assertThat(fakeSender.sent).hasSize(1);
        assertThat(inboxCount()).isEqualTo(1);
    }

    @Test
    void 푸시_내용은_기본값이면_가리고_알림함에는_원래_문구가_남는다() {
        subscribe(jisuId, ENDPOINT);
        Long id = reminderAt(jisuId, NOW);

        scheduler.run();

        assertThat(fakeSender.sent.get(0).title()).isEqualTo("당당");
        assertThat(fakeSender.sent.get(0).body()).isEqualTo("당당 알림이 왔어요");
        assertThat(fakeSender.sent.get(0).url()).isEqualTo("/today?sheet=glucose&reminderId=" + id);
        assertThat(fakeSender.sent.get(0).reminderId()).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT body FROM notifications", String.class))
                .isEqualTo("점심 식후 혈당을 재 주세요");
    }

    @Test
    void 구독이_없으면_SKIPPED이고_알림함에는_남긴다() {
        Long id = reminderAt(jisuId, NOW);

        scheduler.run();

        assertThat(statusOf(id)).isEqualTo("SKIPPED");
        assertThat(jdbc.queryForObject("SELECT sent_at FROM reminders WHERE id = ?", Timestamp.class, id)).isNull();
        assertThat(inboxCount()).isEqualTo(1);
    }

    @Test
    void 측정_알림을_끄면_SKIPPED이고_보내지도_남기지도_않는다() {
        jdbc.update("INSERT INTO notification_preferences (user_id, measurement_enabled) VALUES (?, false)", jisuId);
        subscribe(jisuId, ENDPOINT);
        Long id = reminderAt(jisuId, NOW);

        scheduler.run();

        assertThat(statusOf(id)).isEqualTo("SKIPPED");
        assertThat(fakeSender.sent).isEmpty();
        assertThat(inboxCount()).isZero();
    }

    @Test
    void 죽은_구독뿐이면_끊고_SKIPPED() {
        Long sub = subscribe(jisuId, ENDPOINT);
        fakeSender.answers.put(ENDPOINT, PushSendResult.GONE);
        Long id = reminderAt(jisuId, NOW);

        scheduler.run();

        assertThat(statusOf(id)).isEqualTo("SKIPPED");
        assertThat(jdbc.queryForObject("SELECT revoked_at FROM push_subscriptions WHERE id = ?", Timestamp.class, sub))
                .isNotNull();
    }

    @Test
    void 한_대라도_성공하면_SENT() {
        subscribe(jisuId, ENDPOINT);
        subscribe(jisuId, ENDPOINT_B);
        fakeSender.answers.put(ENDPOINT, PushSendResult.GONE);
        Long id = reminderAt(jisuId, NOW);

        scheduler.run();

        assertThat(statusOf(id)).isEqualTo("SENT");
        assertThat(inboxCount()).isEqualTo(1);
    }

    @Test
    void 잠깐_실패는_5분까지_다시_시도하고_그_뒤엔_SKIPPED() {
        subscribe(jisuId, ENDPOINT);
        fakeSender.answers.put(ENDPOINT, PushSendResult.FAILED);
        Long id = reminderAt(jisuId, NOW);

        scheduler.run();
        assertThat(statusOf(id)).isEqualTo("SCHEDULED");
        assertThat(inboxCount()).isZero();

        clock.set(NOW.plus(Duration.ofMinutes(4)).plusSeconds(59));
        scheduler.run();
        assertThat(statusOf(id)).isEqualTo("SCHEDULED");

        clock.set(NOW.plus(Duration.ofMinutes(5)));
        scheduler.run();
        assertThat(statusOf(id)).isEqualTo("SKIPPED");
        assertThat(inboxCount()).isEqualTo(1);
    }

    @Test
    void 잠깐_실패_뒤_다시_되면_SENT() {
        subscribe(jisuId, ENDPOINT);
        fakeSender.answers.put(ENDPOINT, PushSendResult.FAILED);
        Long id = reminderAt(jisuId, NOW);
        scheduler.run();

        fakeSender.answers.clear();
        clock.set(NOW.plusSeconds(30));
        scheduler.run();

        assertThat(statusOf(id)).isEqualTo("SENT");
        assertThat(inboxCount()).isEqualTo(1);
    }

    @Test
    void 한_사람_발송이_터져도_다음_사람은_보낸다() {
        subscribe(jisuId, ENDPOINT);
        subscribe(minjiId, ENDPOINT_B);
        fakeSender.explodes.add(ENDPOINT);
        Long broken = reminderAt(jisuId, NOW.minusSeconds(10));
        Long fine = reminderAt(minjiId, NOW);

        scheduler.run();

        assertThat(statusOf(broken)).isEqualTo("SCHEDULED");
        assertThat(statusOf(fine)).isEqualTo("SENT");
    }
}
