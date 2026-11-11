package com.dangdang.domain.reminder;

import com.dangdang.domain.push.service.PushSubscriptionService;
import com.dangdang.domain.reminder.dto.response.NextReminderResponse;
import com.dangdang.domain.reminder.enums.ReminderStatus;
import com.dangdang.domain.reminder.service.ReminderQueryService;
import com.dangdang.support.ReminderTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderFunctionsTest extends ReminderTestBase {

    @Autowired ReminderQueryService reminderQueryService;
    @Autowired PushSubscriptionService pushSubscriptionService;

    @Test
    void findNext는_내_예약_중_가장_빠른_것() {
        reminderAt(minjiId, NOW.plus(Duration.ofMinutes(1)));
        Long sent = reminderAt(jisuId, NOW.plus(Duration.ofMinutes(5)));
        setStatus(sent, "SENT");
        reminderAt(jisuId, NOW.plus(Duration.ofHours(2)));
        Long next = reminderAt(jisuId, NOW.plus(Duration.ofHours(1)));

        NextReminderResponse response = reminderQueryService.findNext(jisuId);

        assertThat(response.reminderId()).isEqualTo(next);
        assertThat(response.scheduledAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(response.status()).isEqualTo(ReminderStatus.SCHEDULED);
    }

    @Test
    void 예약이_없으면_findNext는_null() {
        assertThat(reminderQueryService.findNext(jisuId)).isNull();
    }

    @Test
    void hasActive는_살아_있는_구독이_있을_때만_true() {
        assertThat(pushSubscriptionService.hasActive(jisuId)).isFalse();

        Long sub = subscribe(jisuId, ENDPOINT);
        assertThat(pushSubscriptionService.hasActive(jisuId)).isTrue();
        assertThat(pushSubscriptionService.hasActive(minjiId)).isFalse();

        pushSubscriptionService.unsubscribe(jisuId, sub);
        assertThat(pushSubscriptionService.hasActive(jisuId)).isFalse();
    }
}
