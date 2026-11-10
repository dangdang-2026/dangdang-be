package com.dangdang.support;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.push.entity.PushSubscription;
import com.dangdang.domain.push.repository.PushSubscriptionRepository;
import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.domain.reminder.repository.ReminderRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// 지수·민지 두 사람과, 고정 시계(한국 13:00)·가짜 발송기를 준비한다
@SpringBootTest
@AutoConfigureMockMvc
@Import(ReminderTestConfig.class)
public abstract class ReminderTestBase {

    protected static final Instant NOW = Instant.parse("2026-11-09T04:00:00Z");   // 한국 13:00
    protected static final String ENDPOINT = "https://web.push.apple.com/device-a";
    protected static final String ENDPOINT_B = "https://fcm.googleapis.com/fcm/send/device-b";

    @Autowired protected MockMvc mvc;
    @Autowired protected UserRepository userRepository;
    @Autowired protected ReminderRepository reminderRepository;
    @Autowired protected PushSubscriptionRepository pushSubscriptionRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected StringRedisTemplate redis;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected MutableClock clock;
    @Autowired protected FakeWebPushSender fakeSender;

    protected Long jisuId;
    protected Long minjiId;

    @BeforeEach
    void prepare() {
        clock.set(NOW);
        jisuId = saveUser("jisu@x.com", "지수");
        minjiId = saveUser("minji@x.com", "민지");
    }

    @AfterEach
    void cleanAll() { // users를 지우면 reminders·notifications·push_subscriptions·설정도 같이 지워진다
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        fakeSender.clear();
    }

    private Long saveUser(String email, String name) {
        return userRepository.save(User.ofEmail(email, name, passwordEncoder.encode("abcd1234"), Instant.now())).getId();
    }

    protected Cookie login(String email) throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    protected Long subscribe(Long userId, String endpoint) {
        User user = userRepository.findById(userId).orElseThrow();
        return pushSubscriptionRepository.save(
                PushSubscription.of(user, endpoint, "key", "secret", null, Instant.now())).getId();
    }

    protected Long reminderAt(Long userId, Instant scheduledAt) {
        User user = userRepository.findById(userId).orElseThrow();
        return reminderRepository.save(
                Reminder.scheduled(user, null, MeasureSlot.LUNCH_AFTER, scheduledAt, Instant.now())).getId();
    }

    protected void setStatus(Long reminderId, String status) {
        jdbc.update("UPDATE reminders SET status = ? WHERE id = ?", status, reminderId);
    }

    protected String statusOf(Long reminderId) {
        return jdbc.queryForObject("SELECT status FROM reminders WHERE id = ?", String.class, reminderId);
    }

    protected int inboxCount() {
        return jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class);
    }
}
