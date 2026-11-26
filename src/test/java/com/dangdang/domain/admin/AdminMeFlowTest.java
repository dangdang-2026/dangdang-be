package com.dangdang.domain.admin;

import com.dangdang.domain.user.entity.User;
import com.dangdang.support.ReminderTestBase;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 운영자 화면 로그인: 운영자면 이름과 이메일, 아니면 403 (운영 v2 · 로그인 「운영진 계정이 아니에요」)
class AdminMeFlowTest extends ReminderTestBase {

    @Test
    void 운영자면_이름과_이메일을_준다() throws Exception {
        userRepository.save(User.ofEmail("admin@x.com", "당당 운영진", passwordEncoder.encode("abcd1234"), Instant.now()));
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = 'admin@x.com'");

        mvc.perform(get("/api/admin/me").cookie(login("admin@x.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("당당 운영진"))
                .andExpect(jsonPath("$.data.email").value("admin@x.com"));
    }

    @Test
    void 운영자가_아니면_403_로그인을_안_했으면_401() throws Exception {
        mvc.perform(get("/api/admin/me").cookie(login("jisu@x.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUTH_40301"));
        mvc.perform(get("/api/admin/me")).andExpect(status().isUnauthorized());
    }
}
