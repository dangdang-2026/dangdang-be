package com.dangdang.domain.chat.infra;

import com.dangdang.global.config.property.AiProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class AiChatClient implements ChatClient {

    private final AiProperties properties;
    private final RestClient restClient;

    public AiChatClient(AiProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);   // 연결 2초
        factory.setReadTimeout(15_000);     // 응답 15초 (GPT가 도구를 고르고 답을 쓰는 시간 + 여유)
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }

    // 실패하면 RestClientException이 그대로 나간다 → 서비스가 받아서 Q-08 「답을 만들지 못함」
    @Override
    public Reply ask(String requestId, String message, boolean recordsAllowed, List<Turn> history) {
        return restClient.post()
                .uri("/internal/chat")
                .header("X-Internal-Token", properties.internalToken())
                .header("X-Request-Id", requestId)        // AI 서버 로그와 맞춰 보는 번호
                .contentType(MediaType.APPLICATION_JSON)
                .body(new Body(message, recordsAllowed, history))
                .retrieve()
                .body(Reply.class);
    }

    private record Body(String message, boolean recordsAllowed, List<Turn> history) {}
}
