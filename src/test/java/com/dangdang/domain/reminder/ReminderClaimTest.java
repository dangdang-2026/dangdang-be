package com.dangdang.domain.reminder;

import com.dangdang.domain.push.enums.PushSendResult;
import com.dangdang.domain.reminder.scheduler.ReminderScheduler;
import com.dangdang.support.ReminderTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// 「보내는 중」 표시(claimed_until) — 한 바퀴가 ShedLock 5분을 넘겨 다른 서버가 같이 돌아도 같은 알림은 한 번만 간다
// 다른 서버가 잡은 상황은 claimed_until 을 직접 넣어서 만든다
class ReminderClaimTest extends ReminderTestBase {

    @Autowired ReminderScheduler scheduler;

    private void claimedByOtherServerUntil(Long id, Instant until) {
        jdbc.update("UPDATE reminders SET claimed_until = ? WHERE id = ?", Timestamp.from(until), id);
    }

    private Instant claimedUntil(Long id) {
        Timestamp t = jdbc.queryForObject("SELECT claimed_until FROM reminders WHERE id = ?", Timestamp.class, id);
        return t == null ? null : t.toInstant();
    }

    @Test
    void 다른_서버가_보내는_중이면_건너뛴다() {
        subscribe(jisuId, ENDPOINT);
        Long id = reminderAt(jisuId, NOW);
        claimedByOtherServerUntil(id, NOW.plus(Duration.ofMinutes(1)));

        scheduler.run();

        assertThat(fakeSender.sent).isEmpty();
        assertThat(statusOf(id)).isEqualTo("SCHEDULED");
        assertThat(inboxCount()).isZero();
    }

    @Test
    void 잡은_서버가_죽어서_표시가_지나면_다른_서버가_보낸다() {
        subscribe(jisuId, ENDPOINT);
        Long id = reminderAt(jisuId, NOW);
        claimedByOtherServerUntil(id, NOW.minusSeconds(1));

        scheduler.run();

        assertThat(fakeSender.sent).hasSize(1);
        assertThat(statusOf(id)).isEqualTo("SENT");
    }

    @Test
    void 보낼_때_2분짜리_표시를_남긴다() {
        subscribe(jisuId, ENDPOINT);
        Long id = reminderAt(jisuId, NOW);

        scheduler.run();

        assertThat(claimedUntil(id)).isEqualTo(NOW.plus(Duration.ofMinutes(2)));
    }

    @Test
    void 잠깐_실패하면_표시를_풀어서_다음_바퀴에_다시_시도한다() {
        subscribe(jisuId, ENDPOINT);
        fakeSender.answers.put(ENDPOINT, PushSendResult.FAILED);
        Long id = reminderAt(jisuId, NOW);

        scheduler.run();

        assertThat(statusOf(id)).isEqualTo("SCHEDULED");
        assertThat(claimedUntil(id)).isNull();
    }

    @Test
    void 미루면_표시가_지워져서_미룬_시각에_다시_보낸다() {
        subscribe(jisuId, ENDPOINT);
        Long id = reminderAt(jisuId, NOW);
        scheduler.run();                                    // 13:00 보냄 → 13:02까지 표시
        reminderRepository.findById(id).ifPresent(r -> {
            r.snooze(NOW.plus(Duration.ofMinutes(1)));      // 13:01로 미룸 (표시보다 이른 시각)
            reminderRepository.save(r);
        });
        fakeSender.sent.clear();

        clock.set(NOW.plus(Duration.ofMinutes(1)));
        scheduler.run();

        assertThat(fakeSender.sent).hasSize(1);
        assertThat(statusOf(id)).isEqualTo("SENT");
    }

    @Test
    void 재알림도_다른_서버가_잡고_있으면_건너뛴다() {
        subscribe(jisuId, ENDPOINT);
        Long id = reminderAt(jisuId, NOW);
        scheduler.run();                                    // 13:00 첫 발송
        fakeSender.sent.clear();
        Instant tenLater = NOW.plus(Duration.ofMinutes(10));
        claimedByOtherServerUntil(id, tenLater.plus(Duration.ofMinutes(1)));

        clock.set(tenLater);
        scheduler.run();

        assertThat(fakeSender.sent).isEmpty();
        assertThat(jdbc.queryForObject("SELECT renotified FROM reminders WHERE id = ?", Boolean.class, id)).isFalse();
    }
}
