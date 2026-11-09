package com.dangdang.domain.push.infra;

import com.dangdang.domain.push.entity.PushSubscription;
import com.dangdang.domain.push.enums.PushSendResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

//로컬 테스트용
@Slf4j
@Component
@ConditionalOnProperty(name = "push.sender", havingValue = "log", matchIfMissing = true)
public class LogWebPushSender implements WebPushSender {

    @Override
    public PushSendResult send(PushSubscription subscription, PushMessage message) {
        log.info("[푸시] 구독 {} → {} / {}", subscription.getId(), message.title(), message.body());
        return PushSendResult.SENT;
    }
}
