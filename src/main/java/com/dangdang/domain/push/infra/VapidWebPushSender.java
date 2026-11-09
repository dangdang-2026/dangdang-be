package com.dangdang.domain.push.infra;

import com.dangdang.domain.push.entity.PushSubscription;
import com.dangdang.domain.push.enums.PushSendResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

// 진짜 발송 — push.sender=webpush 일 때만 켜지게
@Slf4j
@Component
@ConditionalOnProperty(name = "push.sender", havingValue = "webpush")
@RequiredArgsConstructor
public class VapidWebPushSender implements WebPushSender {

    private static final int TTL_SECONDS = 60 * 60;   // 1시간 안에 못 전하면 버린다 (늦은 측정 알림은 소용없음)
    private static final int TIMEOUT_SECONDS = 10;    // 푸시 서버가 답이 없어도 10초만 기다린다

    private final PushService pushService;
    private final ObjectMapper objectMapper;

    @Override
    public PushSendResult send(PushSubscription subscription, PushMessage message) {
        try {
            byte[] payload = objectMapper.writeValueAsString(message).getBytes(StandardCharsets.UTF_8);
            Notification notification = new Notification(subscription.getEndpoint(), subscription.getP256dh(),
                    subscription.getAuthSecret(), payload, TTL_SECONDS);
            int status = pushService.sendAsync(notification, Encoding.AES128GCM)
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .getStatusLine().getStatusCode();
            return toResult(subscription.getId(), status);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();       // 멈추라는 신호를 지우지 않고 그대로 남긴다
            log.warn("[푸시] 구독 {} 발송 중단", subscription.getId());
            return PushSendResult.FAILED;
        } catch (Exception e) {
            // 한 기기가 실패해도 그 사람의 다른 기기에는 계속 보내야 해서 여기서 끝낸다
            log.warn("[푸시] 구독 {} 발송 실패: {}", subscription.getId(), e.getClass().getSimpleName());
            return PushSendResult.FAILED;
        }
    }

    private PushSendResult toResult(Long subscriptionId, int status) {
        if (status == 404 || status == 410) {
            return PushSendResult.GONE;
        }
        if (status >= 200 && status < 300) {
            return PushSendResult.SENT;
        }
        log.warn("[푸시] 구독 {} 푸시 서버 응답 {}", subscriptionId, status);
        return PushSendResult.FAILED;
    }
}
