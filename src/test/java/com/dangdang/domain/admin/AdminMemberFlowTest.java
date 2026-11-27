package com.dangdang.domain.admin;

import com.dangdang.domain.admin.service.AdminMemberQueryService;
import com.dangdang.domain.user.entity.User;
import com.dangdang.support.ReminderTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 운영자 회원 목록·상세. 지금은 2026-11-09(월) 한국 13:00
class AdminMemberFlowTest extends ReminderTestBase {

    Cookie admin;
    Long adminId;
    Long hanaId;
    Long jisuProfile;
    Long minjiProfile;
    Long reporterProfile;

    @BeforeEach
    void setUp() throws Exception {
        adminId = userRepository.save(User.ofEmail("admin@x.com", "다현", passwordEncoder.encode("abcd1234"), clock.instant())).getId();
        hanaId = userRepository.save(User.ofEmail("hana@x.com", "하나", passwordEncoder.encode("abcd1234"), clock.instant())).getId();
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", adminId);
        admin = login("admin@x.com");
        // 가입 순서: 지수(11. 1.) → 민지(11. 3.) → 하나(11. 5.). 하나는 커뮤니티 프로필이 없다
        jdbc.update("UPDATE users SET created_at = ? WHERE id = ?", at("2026-11-01T00:00:00Z"), jisuId);
        jdbc.update("UPDATE users SET created_at = ? WHERE id = ?", at("2026-11-03T00:00:00Z"), minjiId);
        jdbc.update("UPDATE users SET created_at = ? WHERE id = ?", at("2026-11-05T00:00:00Z"), hanaId);
        jisuProfile = jdbc.queryForObject("INSERT INTO community_profiles (user_id, nickname) VALUES (?, '봄날맘') RETURNING id", Long.class, jisuId);
        minjiProfile = jdbc.queryForObject("INSERT INTO community_profiles (user_id, nickname) VALUES (?, '새벽산책') RETURNING id", Long.class, minjiId);
        // 신고한 사람은 커뮤니티 프로필로 남는다 (reports.reporter_id → community_profiles). 운영자 프로필을 신고자로 씀
        reporterProfile = jdbc.queryForObject("INSERT INTO community_profiles (user_id, nickname) VALUES (?, '신고자') RETURNING id", Long.class, adminId);
    }

    // 글·댓글·신고는 회원을 지워도 남아서 (탈퇴해도 글은 남는 구조) 다른 테스트에 섞이지 않게 지운다
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM reports");
        jdbc.update("DELETE FROM comments");
        jdbc.update("DELETE FROM posts");
    }

    private static Timestamp at(String iso) {
        return iso == null ? null : Timestamp.from(Instant.parse(iso));
    }

    private Long post(Long profileId, String title, String hiddenReason) {
        return jdbc.queryForObject("INSERT INTO posts (profile_id, category, title, content, hidden_reason)"
                + " VALUES (?, 'MEAL', ?, '내용', ?) RETURNING id", Long.class, profileId, title, hiddenReason);
    }

    private Long comment(Long postId, Long profileId, String content) {
        return jdbc.queryForObject("INSERT INTO comments (post_id, profile_id, content) VALUES (?, ?, ?) RETURNING id",
                Long.class, postId, profileId, content);
    }

    private void report(Long reporterProfileId, String type, Long targetId, String reason, String status, String createdAt) {
        jdbc.update("INSERT INTO reports (reporter_id, target_type, target_id, reason, status, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                reporterProfileId, type, targetId, reason, status, at(createdAt));
    }

    private void sanction(Long userId, String reason, String startsAt, String endsAt, String liftedAt) {
        jdbc.update("INSERT INTO user_sanctions (user_id, reason, starts_at, ends_at, lifted_at, created_by) VALUES (?, ?, ?, ?, ?, ?)",
                userId, reason, at(startsAt), at(endsAt), at(liftedAt), adminId);
    }

    private void seed() {
        // 지수: 글 2(하나는 가려짐), 받은 신고 3(글 2 + 댓글 1), 지금 제한 중(11. 16.까지) + 예전에 끝난 제한
        Long ad = post(jisuProfile, "차 공동구매해요", "AD_SALES");
        Long meal = post(jisuProfile, "아침 메뉴", null);
        Long reply = comment(meal, jisuProfile, "프로필 보시고 연락 주세요. 할인 링크 드려요");
        report(minjiProfile, "POST", ad, "AD_SALES", "HIDDEN", "2026-11-07T00:00:00Z");
        report(reporterProfile, "POST", ad, "AD_SALES", "HIDDEN", "2026-11-07T01:00:00Z");
        report(minjiProfile, "COMMENT", reply, "AD_SALES", "RECEIVED", "2026-11-08T00:00:00Z");
        sanction(jisuId, "AD_SALES", "2026-11-09T01:00:00Z", "2026-11-16T01:00:00Z", null);
        sanction(jisuId, "ABUSE", "2026-11-02T00:00:00Z", "2026-11-04T00:00:00Z", null);
        // 민지: 글 1, 댓글 1, 신고 없음, 제한 없음 (풀린 제한만)
        Long walk = post(minjiProfile, "산책했어요", null);
        comment(walk, minjiProfile, "같이 걸어요");
        sanction(minjiId, "ABUSE", "2026-11-05T00:00:00Z", "2026-11-12T00:00:00Z", "2026-11-06T00:00:00Z");
    }

    @Test
    void 목록은_최근_가입_순이고_운영자는_빠진다() throws Exception {
        seed();
        mvc.perform(get("/api/admin/members").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.restrictedCount").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(3)))
                .andExpect(jsonPath("$.data.items[0].displayName").value("하나"))
                .andExpect(jsonPath("$.data.items[0].nickname").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].email").value("ha***@x.com"))
                .andExpect(jsonPath("$.data.items[0].authProvider").value("EMAIL"))
                .andExpect(jsonPath("$.data.items[1].nickname").value("새벽산책"))
                .andExpect(jsonPath("$.data.items[1].restricted").value(false))
                .andExpect(jsonPath("$.data.items[2].nickname").value("봄날맘"))
                .andExpect(jsonPath("$.data.items[2].restricted").value(true))
                .andExpect(jsonPath("$.data.items[2].restrictionEndsAt").value("2026-11-16T01:00:00Z"))
                .andExpect(jsonPath("$.data.items[2].postCount").value(2))
                .andExpect(jsonPath("$.data.items[2].reportedCount").value(3))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 제한_중_탭과_닉네임_검색() throws Exception {
        seed();
        mvc.perform(get("/api/admin/members").param("status", "RESTRICTED").cookie(admin))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].nickname").value("봄날맘"));
        mvc.perform(get("/api/admin/members").param("q", "새벽").cookie(admin))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].nickname").value("새벽산책"));
        // 프로필이 없어도 부를 이름으로 찾는다. %는 글자 그대로
        mvc.perform(get("/api/admin/members").param("q", "하나").cookie(admin))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
        mvc.perform(get("/api/admin/members").param("q", "%").cookie(admin))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void 쪽을_나누면_다음_쪽이_있는지_알려_준다() throws Exception {
        mvc.perform(get("/api/admin/members").param("size", "2").cookie(admin))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        mvc.perform(get("/api/admin/members").param("size", "2").param("page", "1").cookie(admin))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 상세는_활동_수와_제한_신고_이력() throws Exception {
        seed();
        mvc.perform(get("/api/admin/members/{id}", jisuId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("봄날맘"))
                .andExpect(jsonPath("$.data.email").value("ji***@x.com"))
                .andExpect(jsonPath("$.data.kakaoLinked").value(false))
                .andExpect(jsonPath("$.data.postCount").value(2))
                .andExpect(jsonPath("$.data.commentCount").value(1))
                .andExpect(jsonPath("$.data.hiddenCount").value(1))
                .andExpect(jsonPath("$.data.reportedCount").value(3))
                .andExpect(jsonPath("$.data.activeSanction.reason").value("AD_SALES"))
                .andExpect(jsonPath("$.data.activeSanction.endsAt").value("2026-11-16T01:00:00Z"))
                .andExpect(jsonPath("$.data.sanctions", hasSize(2)))
                .andExpect(jsonPath("$.data.sanctions[0].createdByName").value("다현"))
                .andExpect(jsonPath("$.data.reports", hasSize(3)))
                .andExpect(jsonPath("$.data.reports[0].targetType").value("COMMENT"))
                .andExpect(jsonPath("$.data.reports[0].targetLabel").value("프로필 보시고 연락 주세요. 할인 링크 드려요"))
                .andExpect(jsonPath("$.data.reports[1].targetLabel").value("차 공동구매해요"))
                .andExpect(jsonPath("$.data.reports[1].status").value("HIDDEN"));
    }

    @Test
    void 풀린_제한만_있으면_지금_제한은_없다() throws Exception {
        seed();
        mvc.perform(get("/api/admin/members/{id}", minjiId).cookie(admin))
                .andExpect(jsonPath("$.data.activeSanction").doesNotExist())
                .andExpect(jsonPath("$.data.sanctions", hasSize(1)))
                .andExpect(jsonPath("$.data.reportedCount").value(0))
                .andExpect(jsonPath("$.data.reports", hasSize(0)));
    }

    @Test
    void 프로필이_없으면_활동은_0() throws Exception {
        mvc.perform(get("/api/admin/members/{id}", hanaId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").doesNotExist())
                .andExpect(jsonPath("$.data.postCount").value(0))
                .andExpect(jsonPath("$.data.reports", hasSize(0)));
    }

    @Test
    void 운영자_계정이나_없는_번호는_404() throws Exception {
        mvc.perform(get("/api/admin/members/{id}", adminId).cookie(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_40401"));
        mvc.perform(get("/api/admin/members/{id}", 999999).cookie(admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void 건강정보는_응답에_없다() throws Exception {
        mvc.perform(get("/api/admin/members/{id}", jisuId).cookie(admin))
                .andExpect(jsonPath("$.data.readings").doesNotExist())
                .andExpect(jsonPath("$.data.meals").doesNotExist())
                .andExpect(jsonPath("$.data.dueDate").doesNotExist());
    }

    @Test
    void 운영자가_아니면_403() throws Exception {
        mvc.perform(get("/api/admin/members").cookie(login("jisu@x.com")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/members/{id}", minjiId).cookie(login("jisu@x.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void 이메일_가리기() {
        assertThat(AdminMemberQueryService.maskEmail("a@x.com")).isEqualTo("a***@x.com");
        assertThat(AdminMemberQueryService.maskEmail(null)).isNull();
    }
}
