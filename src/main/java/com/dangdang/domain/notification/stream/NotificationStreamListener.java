package com.dangdang.domain.notification.stream;

import com.dangdang.domain.notification.event.NotificationCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class NotificationStreamListener {

    private final NotificationStreamRegistry registry;

    // 알림함 저장이 커밋된 뒤에만 보낸다 — 되돌려진 알림이 배너로 뜨지 않게
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(NotificationCreatedEvent event) {
        registry.send(event.userId(), event.notification());
    }

    // Nginx 기본 proxy_read_timeout(60초)보다 짧게
    // ShedLock(@SchedulerLock)을 붙이지 않는다 — 연결은 서버마다 따로라서, 서버마다 자기 연결에 ping 을 보내야 한다
    @Scheduled(initialDelay = 25, fixedDelay = 25, timeUnit = TimeUnit.SECONDS)
    public void heartbeat() {
        registry.heartbeat();
    }

    // 끄라는 신호를 받으면(블루그린 전환 등) 웹 서버가 멈추기 전에 연결부터 닫는다
    @EventListener(ContextClosedEvent.class)
    public void onShutdown() {
        registry.closeAll();
    }
}
