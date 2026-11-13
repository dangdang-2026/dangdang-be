package com.dangdang.domain.push;

import com.dangdang.domain.push.entity.PushSubscription;
import com.dangdang.domain.push.enums.PushSendResult;
import com.dangdang.domain.push.infra.PushMessage;
import com.dangdang.domain.push.infra.WebPushSender;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PushSubscriptionFlowTest {

    static final String ENDPOINT = "https://web.push.apple.com/device-a";

    // 진짜 푸시 서버 대신: 보낸 내용을 적어 두고, 주소별로 정해 둔 대답을 돌려준다
    static class FakeSender implements WebPushSender {
        final List<PushMessage> sent = new ArrayList<>();
        final Map<String, PushSendResult> answers = new HashMap<>();

        @Override
        public PushSendResult send(PushSubscription subscription, PushMessage message) {
            sent.add(message);
            return answers.getOrDefault(subscription.getEndpoint(), PushSendResult.SENT);
        }
    }

    @TestConfiguration
    static class FakePush {
        @Bean
        @Primary
        FakeSender fakeSender() {
            return new FakeSender();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;
    @Autowired FakeSender fakeSender;

    Long jisuId;
    Cookie jisu;
    Cookie minji;

    @BeforeEach
    void loginTwoPeople() throws Exception {
        jisuId = saveUser("jisu@x.com", "지수");
        saveUser("minji@x.com", "민지");
        jisu = login("jisu@x.com");
        minji = login("minji@x.com");
    }

    @AfterEach
    void clean() { // users를 지우면 push_subscriptions도 같이 지워진다 (ON DELETE CASCADE)
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        fakeSender.sent.clear();
        fakeSender.answers.clear();
    }

    private Long saveUser(String email, String name) {
        return userRepository.save(User.ofEmail(email, name, passwordEncoder.encode("abcd1234"), Instant.now())).getId();
    }

    private Cookie login(String email) throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    private static String body(String endpoint, String p256dh) {
        return "{\"endpoint\":\"" + endpoint + "\",\"keys\":{\"p256dh\":\"" + p256dh + "\",\"auth\":\"secret\"},"
                + "\"userAgent\":\"iPhone Safari\"}";
    }

    private ResultActions subscribe(Cookie who, String json) throws Exception {
        return mvc.perform(post("/api/push-subscriptions").cookie(who)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Long subscribeOk(Cookie who, String endpoint) throws Exception {
        subscribe(who, body(endpoint, "key-1")).andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM push_subscriptions WHERE endpoint = ?", Long.class, endpoint);
    }

    private ResultActions sendTest(Cookie who) throws Exception {
        return mvc.perform(post("/api/push-subscriptions/test").cookie(who));
    }

    private Timestamp revokedAt(Long id) {
        return jdbc.queryForObject("SELECT revoked_at FROM push_subscriptions WHERE id = ?", Timestamp.class, id);
    }

    private int rowCount() {
        return jdbc.queryForObject("SELECT count(*) FROM push_subscriptions", Integer.class);
    }

    @Test
    void 구독하면_201이고_한_줄이_저장된다() throws Exception {
        subscribe(jisu, body(ENDPOINT, "key-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.subscriptionId").isNumber());

        var row = jdbc.queryForMap("SELECT user_id, p256dh, auth_secret, user_agent, revoked_at FROM push_subscriptions");
        assertThat(row.get("user_id")).isEqualTo(jisuId);
        assertThat(row.get("p256dh")).isEqualTo("key-1");
        assertThat(row.get("auth_secret")).isEqualTo("secret");
        assertThat(row.get("user_agent")).isEqualTo("iPhone Safari");
        assertThat(row.get("revoked_at")).isNull();
    }

    @Test
    void 같은_주소로_다시_구독하면_같은_줄을_다시_살리고_키를_바꾼다() throws Exception {
        Long id = subscribeOk(jisu, ENDPOINT);
        mvc.perform(delete("/api/push-subscriptions/" + id).cookie(jisu)).andExpect(status().isOk());
        assertThat(revokedAt(id)).isNotNull();

        subscribe(jisu, body(ENDPOINT, "key-2"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subscriptionId").value(id));

        assertThat(rowCount()).isEqualTo(1);
        assertThat(revokedAt(id)).isNull();
        assertThat(jdbc.queryForObject("SELECT p256dh FROM push_subscriptions WHERE id = ?", String.class, id))
                .isEqualTo("key-2");
    }

    @Test
    void 같은_기기에서_다른_사람이_구독하면_주인이_바뀐다() throws Exception {
        Long id = subscribeOk(jisu, ENDPOINT);

        subscribe(minji, body(ENDPOINT, "key-1")).andExpect(status().isCreated());

        assertThat(rowCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT u.email FROM push_subscriptions p JOIN users u ON u.id = p.user_id"
                + " WHERE p.id = ?", String.class, id)).isEqualTo("minji@x.com");
    }

    @Test
    void 내_구독을_끊으면_revoked_at이_찍힌다() throws Exception {
        Long id = subscribeOk(jisu, ENDPOINT);

        mvc.perform(delete("/api/push-subscriptions/" + id).cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(revokedAt(id)).isNotNull();
        assertThat(rowCount()).isEqualTo(1);   // 줄은 남는다
    }

    @Test
    void 남의_구독이나_없는_번호를_끊으면_404() throws Exception {
        Long id = subscribeOk(jisu, ENDPOINT);

        mvc.perform(delete("/api/push-subscriptions/" + id).cookie(minji))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PUSH_40401"));
        mvc.perform(delete("/api/push-subscriptions/999999").cookie(jisu))
                .andExpect(status().isNotFound());

        assertThat(revokedAt(id)).isNull();
    }

    @Test
    void 푸시_서버_주소가_아니거나_키가_없으면_400() throws Exception {
        for (String json : List.of(
                body("http://web.push.apple.com/a", "k"),
                body("https://169.254.169.254/latest", "k"),
                body("https://web.push.apple.com.evil.com/a", "k"),
                "{\"endpoint\":\"" + ENDPOINT + "\"}",
                body(ENDPOINT, ""))) {
            subscribe(jisu, json)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        }
        assertThat(rowCount()).isZero();
    }

    @Test
    void 테스트_알림은_기본값이면_문구를_가려서_보낸다() throws Exception {
        subscribeOk(jisu, ENDPOINT);

        sendTest(jisu)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sentCount").value(1))
                .andExpect(jsonPath("$.data.revokedCount").value(0));

        assertThat(fakeSender.sent).hasSize(1);
        assertThat(fakeSender.sent.get(0).title()).isEqualTo("당당");
        assertThat(fakeSender.sent.get(0).body()).isEqualTo("확인할 일이 있어요");
    }

    @Test
    void 문구_가리기를_끈_사람에게는_원래_문구를_보낸다() throws Exception {
        jdbc.update("INSERT INTO notification_preferences (user_id, hide_preview) VALUES (?, false)", jisuId);
        subscribeOk(jisu, ENDPOINT);

        sendTest(jisu).andExpect(status().isOk());

        assertThat(fakeSender.sent.get(0).body()).isEqualTo("테스트 알림이에요. 잘 왔다면 설정 끝!");
    }

    @Test
    void 푸시_서버가_죽은_구독이라고_하면_끊고_다음부터_안_보낸다() throws Exception {
        Long dead = subscribeOk(jisu, ENDPOINT);
        Long alive = subscribeOk(jisu, "https://fcm.googleapis.com/fcm/send/device-b");
        fakeSender.answers.put(ENDPOINT, PushSendResult.GONE);

        sendTest(jisu)
                .andExpect(jsonPath("$.data.sentCount").value(1))
                .andExpect(jsonPath("$.data.revokedCount").value(1));
        assertThat(revokedAt(dead)).isNotNull();
        assertThat(revokedAt(alive)).isNull();

        fakeSender.sent.clear();
        sendTest(jisu).andExpect(jsonPath("$.data.sentCount").value(1));
        assertThat(fakeSender.sent).hasSize(1);
    }

    @Test
    void 잠깐_실패는_구독을_끊지_않는다() throws Exception {
        Long id = subscribeOk(jisu, ENDPOINT);
        fakeSender.answers.put(ENDPOINT, PushSendResult.FAILED);

        sendTest(jisu)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failedCount").value(1));

        assertThat(revokedAt(id)).isNull();
    }

    @Test
    void 받을_기기가_없으면_404() throws Exception {
        sendTest(jisu)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PUSH_40402"));
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mvc.perform(post("/api/push-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON).content(body(ENDPOINT, "k")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/push-subscriptions/test")).andExpect(status().isUnauthorized());
    }
}
