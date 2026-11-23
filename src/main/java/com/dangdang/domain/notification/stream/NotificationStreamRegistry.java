package com.dangdang.domain.notification.stream;

import com.dangdang.domain.notification.dto.response.NotificationResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// 앱이 열려 있는 동안 붙어 있는 연결(SSE)을 사람별로 들고 있다
// 연결은 서버마다 따로 메모리에 둔다 — 서버를 계속 2대 이상 띄우게 되면 Redis Pub/Sub 으로 바꾸기
@Component
public class NotificationStreamRegistry {

    static final Duration TIMEOUT = Duration.ofMinutes(30);   // 끊기면 브라우저(EventSource)가 알아서 다시 붙는다
    static final int MAX_PER_USER = 5;                        // 탭·기기를 많이 열어도 5개까지만 (오래된 것부터 닫는다)

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
        List<SseEmitter> mine = emitters.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>());
        mine.add(emitter);
        while (mine.size() > MAX_PER_USER) {
            mine.removeFirst().complete();
        }

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));

        // 첫 이벤트를 바로 보내야 프록시(Nginx)가 응답을 붙잡지 않고, 프론트도 "연결됨"을 안다
        sendTo(userId, emitter, SseEmitter.event().name("connected").data("ok"));
        return emitter;
    }

    // 새 알림 한 건을 그 사람의 열린 연결 모두에 보낸다
    public void send(Long userId, NotificationResponse notification) {
        for (SseEmitter emitter : emitters.getOrDefault(userId, List.of())) {
            sendTo(userId, emitter, SseEmitter.event()
                    .id(String.valueOf(notification.id()))
                    .name("notification")
                    .data(notification, MediaType.APPLICATION_JSON));
        }
    }

    // 아무것도 안 오면 프록시가 연결을 끊으므로 주기적으로 주석 한 줄을 보낸다 (죽은 연결도 여기서 걸러진다)
    public void heartbeat() {
        emitters.forEach((userId, list) -> list.forEach(emitter ->
                sendTo(userId, emitter, SseEmitter.event().comment("ping"))));
    }

    // 서버를 끌 때 열린 연결을 먼저 닫는다 — 안 닫으면 graceful shutdown 이 SSE 를 「하던 요청」으로 보고 30초를 꽉 기다린다
    // 닫히면 브라우저가 다시 붙고, 그땐 Nginx 가 새 서버(블루그린의 새 색)로 보낸다
    public void closeAll() {
        emitters.values().forEach(list -> list.forEach(SseEmitter::complete));
        emitters.clear();
    }

    public int connectionCount(Long userId) {
        return emitters.getOrDefault(userId, List.of()).size();
    }

    private boolean sendTo(Long userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
            return true;
        } catch (IOException | IllegalStateException e) {
            // 앱을 닫았거나 네트워크가 끊긴 연결 — 조용히 뺀다
            remove(userId, emitter);
            return false;
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
