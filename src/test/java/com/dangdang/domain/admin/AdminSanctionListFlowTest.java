package com.dangdang.domain.admin;

import com.dangdang.domain.user.entity.User;
import com.dangdang.support.ReminderTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 운영자 이용 제한 목록 (운영 v2 · 이용 제한). 지금은 2026-11-09(월) 한국 13:00
class AdminSanctionListFlowTest extends ReminderTestBase {

    Cookie admin;
    Long adminId;
    Long hanaId;

    @BeforeEach
    void setUp() throws Exception {
        adminId = userRepository.save(User.ofEmail("admin@x.com", "다현", passwordEncoder.encode("abcd1234"), clock.instant())).getId();
        hanaId = userRepository.save(User.ofEmail("hana@x.com", "하나", passwordEncoder.encode("abcd1234"), clock.instant())).getId();
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", adminId);
        admin = login("admin@x.com");
        // 닉네임은 커뮤니티 프로필에 있다. 하나는 프로필이 없다
        jdbc.update("INSERT INTO community_profiles (user_id, nickname) VALUES (?, '봄날맘')", jisuId);
        jdbc.update("INSERT INTO community_profiles (user_id, nickname) VALUES (?, '새벽산책')", minjiId);
    }

    private static Timestamp at(String iso) {
        return iso == null ? null : Timestamp.from(Instant.parse(iso));
    }

    private Long sanction(Long userId, String reason, String startsAt, String endsAt, String liftedAt) {
        return jdbc.queryForObject("INSERT INTO user_sanctions (user_id, reason, starts_at, ends_at, lifted_at, created_by)"
                + " VALUES (?, ?, ?, ?, ?, ?) RETURNING id", Long.class, userId, reason, at(startsAt), at(endsAt), at(liftedAt), adminId);
    }

    private void seed() {
        // 걸려 있는 것: 지수(오늘 18:00 풀림), 민지(11. 16. 풀림 + 이의 신청), 하나(풀 때까지)
        sanction(jisuId, "AD_SALES", "2026-11-02T09:00:00Z", "2026-11-09T09:00:00Z", null);
        Long minji = sanction(minjiId, "MEDICAL_ADVICE", "2026-11-09T01:00:00Z", "2026-11-16T01:00:00Z", null);
        jdbc.update("INSERT INTO sanction_appeals (sanction_id, user_id, content) VALUES (?, ?, '제 경험이었어요')", minji, minjiId);
        sanction(hanaId, "ABUSE", "2026-11-01T00:00:00Z", null, null);
        // 끝난 것: 지수(기간 끝남 11. 4.), 민지(운영자가 11. 8.에 풀었음)
        sanction(jisuId, "NUMBER_COMPARE", "2026-11-01T00:00:00Z", "2026-11-04T00:00:00Z", null);
        sanction(minjiId, "ABUSE", "2026-11-05T00:00:00Z", "2026-11-12T00:00:00Z", "2026-11-08T05:00:00Z");
    }

    @Test
    void 제한_중_목록은_먼저_풀리는_순서() throws Exception {
        seed();
        mvc.perform(get("/api/admin/sanctions").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeCount").value(3))
                .andExpect(jsonPath("$.data.endedCount").value(2))
                .andExpect(jsonPath("$.data.openAppealCount").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(3)))
                .andExpect(jsonPath("$.data.items[0].nickname").value("봄날맘"))
                .andExpect(jsonPath("$.data.items[0].reason").value("AD_SALES"))
                .andExpect(jsonPath("$.data.items[0].endsAt").value("2026-11-09T09:00:00Z"))
                .andExpect(jsonPath("$.data.items[0].createdByName").value("다현"))
                .andExpect(jsonPath("$.data.items[1].nickname").value("새벽산책"))
                .andExpect(jsonPath("$.data.items[1].appealed").value(true))
                .andExpect(jsonPath("$.data.items[2].nickname").doesNotExist())   // 프로필 없음 → 앱 이름으로
                .andExpect(jsonPath("$.data.items[2].displayName").value("하나"))
                .andExpect(jsonPath("$.data.items[2].endsAt").doesNotExist());   // 풀 때까지
    }

    @Test
    void 끝난_제한은_최근에_끝난_순서와_끝난_이유() throws Exception {
        seed();
        mvc.perform(get("/api/admin/sanctions").param("status", "ENDED").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].nickname").value("새벽산책"))
                .andExpect(jsonPath("$.data.items[0].endedBy").value("LIFTED"))
                .andExpect(jsonPath("$.data.items[0].endedAt").value("2026-11-08T05:00:00Z"))
                .andExpect(jsonPath("$.data.items[1].nickname").value("봄날맘"))
                .andExpect(jsonPath("$.data.items[1].endedBy").value("EXPIRED"))
                .andExpect(jsonPath("$.data.items[1].endedAt").value("2026-11-04T00:00:00Z"));
    }

    @Test
    void 비어_있으면_빈_목록() throws Exception {
        mvc.perform(get("/api/admin/sanctions").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeCount").value(0))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void 운영자가_아니면_403() throws Exception {
        mvc.perform(get("/api/admin/sanctions").cookie(login("jisu@x.com")))
                .andExpect(status().isForbidden());
    }
}
