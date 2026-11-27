package com.dangdang.domain.admin.service;

import com.dangdang.domain.admin.dto.response.AdminMemberDetailResponse;
import com.dangdang.domain.admin.dto.response.AdminMemberDetailResponse.Report;
import com.dangdang.domain.admin.dto.response.AdminMemberDetailResponse.Sanction;
import com.dangdang.domain.admin.dto.response.AdminMemberPageResponse;
import com.dangdang.domain.admin.dto.response.AdminMemberPageResponse.Item;
import com.dangdang.domain.admin.enums.MemberListStatus;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// 운영자 회원 목록·상세. 닉네임·제한·신고 수를 한 번에 붙여야 해서 SQL로 바로 읽는다
// 건강정보(혈당·식사·임신·진료)는 읽지 않는다 — 운영자도 볼 이유가 없는 민감정보
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMemberQueryService {

    private static final int REPORTS_SHOWN = 20;

    // 지금 걸린 제한 (AdminSanctionQueryService.ACTIVE 와 같은 기준). ?는 지금 시각 두 번
    private static final String ACTIVE_SANCTION = "s.lifted_at IS NULL AND s.starts_at <= ? AND (s.ends_at IS NULL OR s.ends_at > ?)";

    // 이 회원의 글·댓글이 받은 신고. 프로필이 없으면 글도 없어서 0
    private static final String REPORTED = "(SELECT count(*) FROM reports r WHERE"
            + " (r.target_type = 'POST' AND r.target_id IN (SELECT id FROM posts WHERE profile_id = p.id))"
            + " OR (r.target_type = 'COMMENT' AND r.target_id IN (SELECT id FROM comments WHERE profile_id = p.id)))";

    private static final String LIST = "SELECT u.id, p.nickname, u.display_name, u.email, u.auth_provider, u.created_at,"
            + " s.id AS sanction_id, s.ends_at AS sanction_ends_at,"
            + " (SELECT count(*) FROM posts po WHERE po.profile_id = p.id AND po.deleted_at IS NULL) AS post_count,"
            + " " + REPORTED + " AS reported_count"
            + " FROM users u"
            + " LEFT JOIN community_profiles p ON p.user_id = u.id"
            // 지금 걸린 제한 하나 (겹치면 늦게 풀리는 것)
            + " LEFT JOIN LATERAL (SELECT s.id, s.ends_at FROM user_sanctions s WHERE s.user_id = u.id AND " + ACTIVE_SANCTION
            + "  ORDER BY s.ends_at DESC NULLS FIRST LIMIT 1) s ON true"
            + " WHERE u.role = 'USER'";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AdminMemberPageResponse page(MemberListStatus status, String q, int page, int size) {
        Timestamp now = Timestamp.from(clock.instant());
        long total = count("SELECT count(*) FROM users WHERE role = 'USER'");
        long restricted = count("SELECT count(DISTINCT s.user_id) FROM user_sanctions s JOIN users u ON u.id = s.user_id"
                + " WHERE u.role = 'USER' AND " + ACTIVE_SANCTION, now, now);

        StringBuilder sql = new StringBuilder(LIST);
        List<Object> args = new ArrayList<>(List.of(now, now));
        if (status == MemberListStatus.RESTRICTED) {
            sql.append(" AND s.id IS NOT NULL");
        }
        if (q != null && !q.isBlank()) {
            // %, _ 를 글자 그대로 찾게 (AdminLogQueryService 와 같은 방식)
            String like = "%" + q.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            sql.append(" AND (p.nickname ILIKE ? ESCAPE '\\' OR u.display_name ILIKE ? ESCAPE '\\')");
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY u.created_at DESC, u.id DESC LIMIT ? OFFSET ?");
        args.add(size + 1);   // 하나 더 읽어서 다음 쪽이 있는지 본다
        args.add((long) page * size);

        List<Item> rows = jdbc.query(sql.toString(), (rs, i) -> item(rs), args.toArray());
        boolean hasNext = rows.size() > size;
        return new AdminMemberPageResponse(total, restricted, hasNext ? rows.subList(0, size) : rows, page, size, hasNext);
    }

    public AdminMemberDetailResponse detail(Long userId) {
        List<AdminMemberDetailResponse> found = jdbc.query(
                "SELECT u.id, p.id AS profile_id, p.nickname, u.display_name, u.email, u.auth_provider, u.kakao_linked_at, u.created_at,"
                        + " (SELECT count(*) FROM posts po WHERE po.profile_id = p.id AND po.deleted_at IS NULL) AS post_count,"
                        + " (SELECT count(*) FROM comments c WHERE c.profile_id = p.id AND c.deleted_at IS NULL) AS comment_count,"
                        + " (SELECT count(*) FROM posts po WHERE po.profile_id = p.id AND po.hidden_reason IS NOT NULL)"
                        + " + (SELECT count(*) FROM comments c WHERE c.profile_id = p.id AND c.hidden_reason IS NOT NULL) AS hidden_count,"
                        + " " + REPORTED + " AS reported_count"
                        + " FROM users u LEFT JOIN community_profiles p ON p.user_id = u.id"
                        + " WHERE u.id = ? AND u.role = 'USER'",
                (rs, i) -> {
                    Long profileId = rs.getObject("profile_id", Long.class);
                    List<Sanction> sanctions = sanctions(userId);
                    return new AdminMemberDetailResponse(
                            rs.getLong("id"), rs.getString("nickname"), rs.getString("display_name"),
                            maskEmail(rs.getString("email")), rs.getString("auth_provider"),
                            rs.getTimestamp("kakao_linked_at") != null, instant(rs.getTimestamp("created_at")),
                            rs.getLong("post_count"), rs.getLong("comment_count"), rs.getLong("hidden_count"),
                            rs.getLong("reported_count"), active(sanctions), sanctions,
                            profileId == null ? List.of() : reports(profileId));
                },
                userId);
        if (found.isEmpty()) {
            // 없는 번호와 운영자 계정은 같은 404 (운영자 목록이 아니라서)
            throw new CustomException(UserErrorCode.USER_NOT_FOUND);
        }
        return found.get(0);
    }

    private List<Sanction> sanctions(Long userId) {
        return jdbc.query("SELECT s.id, s.reason, s.starts_at, s.ends_at, s.lifted_at, a.display_name AS created_by_name"
                        + " FROM user_sanctions s LEFT JOIN users a ON a.id = s.created_by"
                        + " WHERE s.user_id = ? ORDER BY s.starts_at DESC, s.id DESC",
                (rs, i) -> new Sanction(rs.getLong("id"), rs.getString("reason"), instant(rs.getTimestamp("starts_at")),
                        instant(rs.getTimestamp("ends_at")), instant(rs.getTimestamp("lifted_at")), rs.getString("created_by_name")),
                userId);
    }

    // 이력 중 지금 걸린 것 (겹치면 늦게 풀리는 것, 풀 때까지가 가장 늦음)
    private Sanction active(List<Sanction> sanctions) {
        Instant now = clock.instant();
        return sanctions.stream()
                .filter(s -> s.liftedAt() == null && !s.startsAt().isAfter(now) && (s.endsAt() == null || s.endsAt().isAfter(now)))
                .max((a, b) -> a.endsAt() == null ? (b.endsAt() == null ? 0 : 1) : b.endsAt() == null ? -1 : a.endsAt().compareTo(b.endsAt()))
                .orElse(null);
    }

    private List<Report> reports(Long profileId) {
        return jdbc.query("SELECT r.id, r.target_type, r.target_id, r.reason, r.status, r.created_at,"
                        + " CASE r.target_type WHEN 'POST' THEN po.title ELSE left(c.content, 40) END AS target_label"
                        + " FROM reports r"
                        + " LEFT JOIN posts po ON r.target_type = 'POST' AND po.id = r.target_id"
                        + " LEFT JOIN comments c ON r.target_type = 'COMMENT' AND c.id = r.target_id"
                        + " WHERE (r.target_type = 'POST' AND po.profile_id = ?) OR (r.target_type = 'COMMENT' AND c.profile_id = ?)"
                        + " ORDER BY r.created_at DESC, r.id DESC LIMIT " + REPORTS_SHOWN,
                (rs, i) -> new Report(rs.getLong("id"), rs.getString("target_type"), rs.getLong("target_id"),
                        rs.getString("target_label"), rs.getString("reason"), rs.getString("status"),
                        instant(rs.getTimestamp("created_at"))),
                profileId, profileId);
    }

    private static Item item(ResultSet rs) throws SQLException {
        Long sanctionId = rs.getObject("sanction_id", Long.class);
        return new Item(rs.getLong("id"), rs.getString("nickname"), rs.getString("display_name"),
                maskEmail(rs.getString("email")), rs.getString("auth_provider"), instant(rs.getTimestamp("created_at")),
                sanctionId != null, instant(rs.getTimestamp("sanction_ends_at")),
                rs.getLong("post_count"), rs.getLong("reported_count"));
    }

    // jisu@x.com → ji***@x.com, a@x.com → a***@x.com. 카카오 가입이라 이메일이 없으면 null
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) return null;
        int at = email.indexOf('@');
        if (at < 0) return "***";
        String local = email.substring(0, at);
        return local.substring(0, Math.min(2, Math.max(1, local.length() - 1))) + "***" + email.substring(at);
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
