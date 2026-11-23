package com.dangdang.domain.notification;

import com.dangdang.domain.notification.stream.NotificationStreamRegistry;
import com.dangdang.domain.reminder.scheduler.ReminderScheduler;
import com.dangdang.support.ReminderTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 앱이 열려 있을 때 새 알림이 SSE 로 바로 오는지 (앱 안 배너)
class NotificationStreamFlowTest extends ReminderTestBase {

    @Autowired ReminderScheduler scheduler;
    @Autowired NotificationStreamRegistry registry;

    Cookie jisu;

    @BeforeEach
    void loginJisu() throws Exception {
        jisu = login("jisu@x.com");
    }

    @AfterEach
    void closeStreams() {   // 테스트마다 연 연결을 닫아 다음 테스트로 넘기지 않는다
        registry.closeAll();
    }

    // 배너 전송은 다른 스레드(@Async)라 조금 기다린다
    private void awaitBody(MvcResult result, String expected) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(result.getResponse().getContentAsString(UTF_8)).contains(expected));
    }

    private MvcResult open(Cookie who) throws Exception {
        return mvc.perform(get("/api/notifications/stream").cookie(who))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    @Test
    void 연결하면_connected_이벤트가_먼저_온다() throws Exception {
        MvcResult result = open(jisu);
        MockHttpServletResponse response = result.getResponse();

        assertThat(response.getContentType()).startsWith("text/event-stream");
        assertThat(response.getHeader("X-Accel-Buffering")).isEqualTo("no");
        assertThat(response.getContentAsString(UTF_8)).contains("event:connected");
        assertThat(registry.connectionCount(jisuId)).isEqualTo(1);
    }

    @Test
    void 측정_알림이_나가면_열린_연결로_바로_온다() throws Exception {
        MvcResult result = open(jisu);
        subscribe(jisuId, ENDPOINT);
        Long reminderId = reminderAt(jisuId, NOW);

        scheduler.run();

        awaitBody(result, "event:notification");
        String body = result.getResponse().getContentAsString(UTF_8);
        assertThat(body).contains("\"type\":\"MEASURE\"");
        assertThat(body).contains("점심 식후 혈당을 잴 시간이에요");
        assertThat(body).contains("reminderId=" + reminderId);
    }

    @Test
    void 푸시_받을_기기가_없어도_앱_안에는_뜬다() throws Exception {
        MvcResult result = open(jisu);
        reminderAt(jisuId, NOW);                 // 웹 푸시 구독 없음

        scheduler.run();

        awaitBody(result, "event:notification");
    }

    @Test
    void 남의_알림은_오지_않는다() throws Exception {
        MvcResult result = open(jisu);
        MvcResult minji = open(login("minji@x.com"));
        subscribe(minjiId, ENDPOINT_B);
        reminderAt(minjiId, NOW);

        scheduler.run();

        awaitBody(minji, "event:notification");   // 민지에게 간 뒤에도
        assertThat(result.getResponse().getContentAsString(UTF_8)).doesNotContain("event:notification");
    }

    @Test
    void 연결은_사람당_5개까지_오래된_것부터_닫는다() throws Exception {
        MvcResult first = open(jisu);
        for (int i = 0; i < 5; i++) {
            open(jisu);
        }

        assertThat(registry.connectionCount(jisuId)).isEqualTo(5);
        assertThat(first.getAsyncResult(1000)).isNull();   // 첫 연결은 닫혔다
    }

    @Test
    void 서버를_끌_때_열린_연결을_먼저_닫는다() throws Exception {
        MvcResult result = open(jisu);

        registry.closeAll();

        assertThat(registry.connectionCount(jisuId)).isZero();
        assertThat(result.getRequest().getAsyncContext()).isNotNull();
        assertThat(result.getAsyncResult(1000)).isNull();   // 응답이 끝났다 (기다리지 않고 바로)
    }

    @Test
    void 토큰_없이는_401() throws Exception {
        mvc.perform(get("/api/notifications/stream")).andExpect(status().isUnauthorized());
    }
}
