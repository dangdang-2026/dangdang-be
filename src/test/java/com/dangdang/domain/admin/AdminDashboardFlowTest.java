package com.dangdang.domain.admin;

import com.dangdang.domain.user.entity.User;
import com.dangdang.support.ReminderTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 운영자 대시보드 (운영 v2 · 대시보드). 지금은 2026-11-09(월) 한국 13:00
class AdminDashboardFlowTest extends ReminderTestBase {

    Cookie admin;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.save(User.ofEmail("admin@x.com", "운영자", passwordEncoder.encode("abcd1234"), clock.instant()));
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = 'admin@x.com'");
        admin = login("admin@x.com");
        // 지수는 이번 주(11. 5.)에 가입, 민지는 그 전에 가입
        jdbc.update("UPDATE users SET created_at = ? WHERE id = ?", at("2026-11-05T01:00:00Z"), jisuId);
        jdbc.update("UPDATE users SET created_at = ? WHERE id = ?", at("2026-10-20T01:00:00Z"), minjiId);
        jdbc.update("INSERT INTO criteria_versions (user_id, measure_slots, effective_from) VALUES (?, '[\"FASTING\"]'::jsonb, '2026-10-01')", jisuId);
    }

    @AfterEach
    void cleanCommunity() { // 회원을 지워도 남는 것들
        jdbc.update("DELETE FROM reports");
        jdbc.update("DELETE FROM posts");
        jdbc.update("DELETE FROM announcements");
    }

    private static Timestamp at(String iso) {
        return Timestamp.from(Instant.parse(iso));
    }

    private void reading(Long userId, String measuredAt, boolean deleted) {
        jdbc.update("INSERT INTO readings (user_id, criteria_version_id, reading_type, measured_at, value, deleted_at)"
                        + " VALUES (?, (SELECT id FROM criteria_versions WHERE user_id = ? LIMIT 1), 'FASTING', ?, 90, ?)",
                userId, userId, at(measuredAt), deleted ? at(measuredAt) : null);
    }

    private void meal(Long userId, String startedAt) {
        jdbc.update("INSERT INTO meals (user_id, meal_slot, started_at) VALUES (?, 'LUNCH', ?)", userId, at(startedAt));
    }

    private Long profile(Long userId, String nickname) {
        return jdbc.queryForObject("INSERT INTO community_profiles (user_id, nickname) VALUES (?, ?) RETURNING id",
                Long.class, userId, nickname);
    }

    private void report(String type, long targetId, String reason, String createdAt) {
        jdbc.update("INSERT INTO reports (target_type, target_id, reason, created_at) VALUES (?, ?, ?, ?)",
                type, targetId, reason, at(createdAt));
    }

    private void seed() {
        // 혈당: 지수 11. 9. 08:00 · 11. 8. 12:00 (한국). 지운 것과 기간 밖은 안 센다
        reading(jisuId, "2026-11-08T23:00:00Z", false);
        reading(jisuId, "2026-11-08T03:00:00Z", false);
        reading(jisuId, "2026-11-09T00:30:00Z", true);
        reading(jisuId, "2026-11-01T03:00:00Z", false);
        // 식사: 민지 11. 7. 12:00
        meal(minjiId, "2026-11-07T03:00:00Z");
        // 글 1개 (11. 9.) + 그 글에 사람 신고 2건, 다른 댓글에 걱정되는 글 신고 1건
        Long jisuProfile = profile(jisuId, "말랑한 두부");
        profile(minjiId, "포근한 고구마");
        Long postId = jdbc.queryForObject("INSERT INTO posts (profile_id, category, title, content, created_at, updated_at)"
                + " VALUES (?, 'MIND', '제목', '본문', ?, ?) RETURNING id", Long.class, jisuProfile, at("2026-11-09T01:00:00Z"), at("2026-11-09T01:00:00Z"));
        report("POST", postId, "MEDICAL_ADVICE", "2026-11-08T05:00:00Z");
        report("POST", postId, "MEDICAL_ADVICE", "2026-11-09T02:00:00Z");
        report("COMMENT", 999L, "CRISIS", "2026-11-09T03:00:00Z");
        // 이용 제한: 민지는 오늘 18:00(한국)에 풀림, 지수는 11. 12.에 풀림 + 이의 신청
        jdbc.update("INSERT INTO user_sanctions (user_id, reason, starts_at, ends_at) VALUES (?, 'AD_SALES', ?, ?)",
                minjiId, at("2026-11-02T09:00:00Z"), at("2026-11-09T09:00:00Z"));
        Long jisuSanction = jdbc.queryForObject("INSERT INTO user_sanctions (user_id, reason, starts_at, ends_at) VALUES (?, 'ABUSE', ?, ?) RETURNING id",
                Long.class, jisuId, at("2026-11-05T00:00:00Z"), at("2026-11-12T00:00:00Z"));
        jdbc.update("INSERT INTO sanction_appeals (sanction_id, user_id, content) VALUES (?, ?, '제 경험이었어요')", jisuSanction, jisuId);
        // 소식: 내일 오전 9:00(한국)에 올라갈 예약 1개, 이미 올라간 것 1개
        jdbc.update("INSERT INTO announcements (slot, kind, title, active_from) VALUES ('SPLASH', 'NEWS', '예약 소식', ?)", at("2026-11-10T00:00:00Z"));
        jdbc.update("INSERT INTO announcements (slot, kind, title, active_from) VALUES ('SPLASH', 'NEWS', '올라간 소식', ?)", at("2026-11-01T00:00:00Z"));
    }

    @Test
    void 처리할_일과_7일_기록_현황() throws Exception {
        seed();
        mvc.perform(get("/api/admin/dashboard").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").value("WEEK"))
                .andExpect(jsonPath("$.data.from").value("2026-11-03"))
                .andExpect(jsonPath("$.data.to").value("2026-11-09"))
                // 처리할 일 — 신고는 글·댓글 단위로 센다
                .andExpect(jsonPath("$.data.todo.pendingReports").value(2))
                .andExpect(jsonPath("$.data.todo.crisisReports").value(1))
                .andExpect(jsonPath("$.data.todo.oldestPendingReportAt").value("2026-11-08T05:00:00Z"))
                .andExpect(jsonPath("$.data.todo.sanctionsEndingToday").value(1))
                .andExpect(jsonPath("$.data.todo.openAppeals").value(1))
                .andExpect(jsonPath("$.data.todo.scheduledAnnouncements").value(1))
                .andExpect(jsonPath("$.data.todo.nextAnnouncementAt").value("2026-11-10T00:00:00Z"))
                // 기록 현황 — 운영자 계정은 산모 수에 안 넣는다
                .andExpect(jsonPath("$.data.summary.totalUsers").value(2))
                .andExpect(jsonPath("$.data.summary.activeUsers").value(2))
                .andExpect(jsonPath("$.data.summary.newUsers").value(1))
                .andExpect(jsonPath("$.data.summary.readings").value(2))
                .andExpect(jsonPath("$.data.summary.meals").value(1))
                .andExpect(jsonPath("$.data.summary.posts").value(1))
                .andExpect(jsonPath("$.data.summary.reports").value(3))
                // 날짜별 — 오래된 날부터 7줄
                .andExpect(jsonPath("$.data.daily", hasSize(7)))
                .andExpect(jsonPath("$.data.daily[0].date").value("2026-11-03"))
                .andExpect(jsonPath("$.data.daily[4].date").value("2026-11-07"))
                .andExpect(jsonPath("$.data.daily[4].activeUsers").value(1))
                .andExpect(jsonPath("$.data.daily[4].meals").value(1))
                .andExpect(jsonPath("$.data.daily[5].readings").value(1))
                .andExpect(jsonPath("$.data.daily[5].reports").value(1))
                .andExpect(jsonPath("$.data.daily[6].date").value("2026-11-09"))
                .andExpect(jsonPath("$.data.daily[6].readings").value(1))
                .andExpect(jsonPath("$.data.daily[6].posts").value(1))
                .andExpect(jsonPath("$.data.daily[6].reports").value(2));
    }

    @Test
    void 오늘과_30일() throws Exception {
        seed();
        mvc.perform(get("/api/admin/dashboard").param("period", "TODAY").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.from").value("2026-11-09"))
                .andExpect(jsonPath("$.data.daily", hasSize(1)))
                .andExpect(jsonPath("$.data.summary.activeUsers").value(1))
                .andExpect(jsonPath("$.data.summary.readings").value(1));
        mvc.perform(get("/api/admin/dashboard").param("period", "MONTH").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.from").value("2026-10-11"))
                .andExpect(jsonPath("$.data.daily", hasSize(30)))
                .andExpect(jsonPath("$.data.summary.readings").value(3));
    }

    @Test
    void 비어_있어도_0으로_채운다() throws Exception {
        mvc.perform(get("/api/admin/dashboard").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todo.pendingReports").value(0))
                .andExpect(jsonPath("$.data.todo.oldestPendingReportAt").doesNotExist())
                .andExpect(jsonPath("$.data.daily", hasSize(7)))
                .andExpect(jsonPath("$.data.daily[6].readings").value(0));
    }

    @Test
    void 운영자가_아니면_403() throws Exception {
        mvc.perform(get("/api/admin/dashboard").cookie(login("jisu@x.com")))
                .andExpect(status().isForbidden());
    }
}
