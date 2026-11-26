package com.dangdang.domain.admin.service;

import com.dangdang.domain.admin.dto.response.AdminLogPageResponse;
import com.dangdang.domain.admin.dto.response.AdminLogPageResponse.Item;
import com.dangdang.domain.admin.enums.AdminLogCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// 운영자 처리 기록 목록. 신고 처리(admin_actions)와 그 밖의 일(admin_logs)을 합쳐 최근 것부터 보여 준다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminLogQueryService {

    // src: 같은 시각이면 admin_logs(1)를 admin_actions(0)보다 위에 — 순서가 매번 같게만 하면 된다
    private static final String LOGS = """
            WITH logs AS (
                SELECT 'REPORT' AS category, 'REPORT_' || a.action AS action, a.occurred_at, a.admin_id,
                       a.target_type, a.target_id,
                       COALESCE(p.title, LEFT(c.content, 40)) AS target_label, pr.nickname AS target_author,
                       a.reason, a.memo, 0 AS src, a.id
                FROM admin_actions a
                LEFT JOIN posts p ON a.target_type = 'POST' AND p.id = a.target_id
                LEFT JOIN comments c ON a.target_type = 'COMMENT' AND c.id = a.target_id
                LEFT JOIN community_profiles pr ON pr.id = COALESCE(p.profile_id, c.profile_id)
                UNION ALL
                SELECT l.target_type, l.action, l.occurred_at, l.admin_id,
                       l.target_type, l.target_id, l.target_label, NULL, l.reason, l.detail, 1, l.id
                FROM admin_logs l
            )
            SELECT g.*, u.display_name AS admin_name
            FROM logs g LEFT JOIN users u ON u.id = g.admin_id
            """;

    private final JdbcTemplate jdbc;

    // 신고 처리 기록의 글 이름은 지금 글에서 읽는다 (가린 글도 운영자에게는 그대로). 그 밖은 그때 적어 둔 이름
    public AdminLogPageResponse page(AdminLogCategory category, String q, int page, int size) {
        StringBuilder sql = new StringBuilder(LOGS).append(" WHERE true");
        List<Object> args = new ArrayList<>();
        if (category != null) {
            sql.append(" AND g.category = ?");
            args.add(category.name());
        }
        if (q != null && !q.isBlank()) {
            // 닉네임이나 제목 일부로 찾는다. % _ 는 글자 그대로 찾게 막는다
            String like = "%" + q.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            sql.append(" AND (g.target_label ILIKE ? ESCAPE '\\' OR g.target_author ILIKE ? ESCAPE '\\')");
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY g.occurred_at DESC, g.src DESC, g.id DESC LIMIT ? OFFSET ?");
        args.add(size + 1);   // 하나 더 읽어서 다음 쪽이 있는지 본다 (전체 개수는 세지 않는다)
        args.add((long) page * size);

        List<Item> rows = jdbc.query(sql.toString(), (rs, i) -> new Item(
                instant(rs.getTimestamp("occurred_at")), rs.getString("admin_name"), rs.getString("category"),
                rs.getString("action"), rs.getString("target_type"), rs.getLong("target_id"),
                rs.getString("target_label"), rs.getString("target_author"), rs.getString("reason"), rs.getString("memo")),
                args.toArray());
        boolean hasNext = rows.size() > size;
        return new AdminLogPageResponse(hasNext ? rows.subList(0, size) : rows, page, size, hasNext);
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
