package com.dangdang.support;

import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.enums.ConsentType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

// 건강 기록을 저장하는 API는 건강정보(SENSITIVE) 동의가 있어야 된다 (SensitiveConsentGuard)
// 테스트 사용자에게 지금 문서 버전으로 동의해 둔다
public final class TestConsents {

    // 아주 옛날 시각으로 — 테스트가 API로 남기는 동의·철회가 늘 이것보다 최근이 되게 (가장 최근 줄이 지금 상태)
    private static final Instant GRANTED_AT = Instant.parse("2000-01-01T00:00:00Z");

    private TestConsents() {
    }

    public static User grantSensitive(JdbcTemplate jdbc, User user) {
        jdbc.update("INSERT INTO consent_events (user_id, consent_type, document_version, granted, occurred_at)"
                        + " VALUES (?, 'SENSITIVE', ?, true, ?)",
                user.getId(), ConsentType.SENSITIVE.getVersion(), Timestamp.from(GRANTED_AT));
        return user;
    }
}
