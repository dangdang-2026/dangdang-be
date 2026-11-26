package com.dangdang.domain.admin.service;

import com.dangdang.domain.admin.dto.response.AdminSanctionListResponse;
import com.dangdang.domain.admin.dto.response.AdminSanctionListResponse.Item;
import com.dangdang.domain.admin.enums.SanctionListStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

// 운영자 이용 제한 목록. 회원·닉네임·건 운영자·이의 신청을 한 번에 붙여야 해서 SQL로 바로 읽는다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSanctionQueryService {

    // 걸려 있음 = 안 풀었고, 시작했고, 기간이 안 끝남 (UserSanctionRepository.findActive 와 같은 기준)
    private static final String ACTIVE = "s.lifted_at IS NULL AND s.starts_at <= ? AND (s.ends_at IS NULL OR s.ends_at > ?)";
    private static final String ENDED = "(s.lifted_at IS NOT NULL OR s.ends_at <= ?)";

    private static final String SELECT = "SELECT s.id, s.user_id, p.nickname, u.display_name, s.reason, s.starts_at, s.ends_at, s.lifted_at,"
            + " a.display_name AS created_by_name,"
            + " EXISTS (SELECT 1 FROM sanction_appeals ap WHERE ap.sanction_id = s.id) AS appealed,"
            // 누가 풀었는지는 처리 기록(admin_logs)에 남는다 — 가장 최근에 푼 사람
            + " (SELECT lu.display_name FROM admin_logs l JOIN users lu ON lu.id = l.admin_id"
            // target_type을 먼저 걸어야 ix_admin_logs_target (target_type, target_id, occurred_at) 인덱스를 탄다 (PR #165 리뷰)
            + "  WHERE l.target_type = 'SANCTION' AND l.target_id = s.id AND l.action = 'SANCTION_LIFT'"
            + "  ORDER BY l.occurred_at DESC LIMIT 1) AS lifted_by_name"
            + " FROM user_sanctions s"
            + " JOIN users u ON u.id = s.user_id"
            + " LEFT JOIN community_profiles p ON p.user_id = s.user_id"
            + " LEFT JOIN users a ON a.id = s.created_by";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AdminSanctionListResponse list(SanctionListStatus status) {
        Timestamp now = Timestamp.from(clock.instant());
        long active = count("SELECT count(*) FROM user_sanctions s WHERE " + ACTIVE, now, now);
        long ended = count("SELECT count(*) FROM user_sanctions s WHERE " + ENDED, now);
        long appeals = count("SELECT count(*) FROM sanction_appeals ap JOIN user_sanctions s ON s.id = ap.sanction_id WHERE " + ACTIVE, now, now);

        List<Item> items = status == SanctionListStatus.ACTIVE
                // 먼저 풀리는 것부터 (풀 때까지는 맨 뒤) — 오늘 풀리는 제한이 맨 위에 보이게
                ? jdbc.query(SELECT + " WHERE " + ACTIVE + " ORDER BY s.ends_at ASC NULLS LAST, s.id",
                        (rs, i) -> item(rs, false), now, now)
                // 최근에 끝난 것부터. 끝난 시각 = 일찍 풀었으면 푼 시각, 아니면 기간 끝
                : jdbc.query(SELECT + " WHERE " + ENDED + " ORDER BY COALESCE(s.lifted_at, s.ends_at) DESC, s.id DESC",
                        (rs, i) -> item(rs, true), now);
        return new AdminSanctionListResponse(active, ended, appeals, items);
    }

    private static Item item(ResultSet rs, boolean ended) throws SQLException {
        Instant endsAt = instant(rs.getTimestamp("ends_at"));
        Instant liftedAt = instant(rs.getTimestamp("lifted_at"));
        String endedBy = !ended ? null : liftedAt != null ? "LIFTED" : "EXPIRED";
        Instant endedAt = !ended ? null : liftedAt != null ? liftedAt : endsAt;
        return new Item(rs.getLong("id"), rs.getLong("user_id"), rs.getString("nickname"), rs.getString("display_name"),
                rs.getString("reason"), instant(rs.getTimestamp("starts_at")), endsAt, liftedAt,
                rs.getString("created_by_name"), rs.getString("lifted_by_name"), rs.getBoolean("appealed"), endedBy, endedAt);
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
