package com.dangdang.domain.user.service;

import com.dangdang.domain.notification.enums.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 철회 때 알림함을 지우는 SQL — 건강 알림 종류가 비어도 `type IN ()` 문법 오류가 나지 않게 (리뷰 #133)
class SensitiveConsentWithdrawalSqlTest {

    @Test
    void 건강_알림_종류만_지운다() {
        String sql = SensitiveConsentWithdrawalService.notificationsSql(Arrays.asList(NotificationType.values()));

        assertThat(sql).startsWith("DELETE FROM notifications WHERE user_id = ? AND type IN (");
        Arrays.stream(NotificationType.values()).filter(NotificationType::isHealth)
                .forEach(type -> assertThat(sql).contains("'" + type.name() + "'"));
    }

    @Test
    void 건강_알림_종류가_없으면_IN_없이_아무_줄도_안_지운다() {
        String sql = SensitiveConsentWithdrawalService.notificationsSql(List.of());

        assertThat(sql).isEqualTo("DELETE FROM notifications WHERE user_id = ? AND 1 = 0");
        assertThat(sql).doesNotContain("IN (");
    }
}
