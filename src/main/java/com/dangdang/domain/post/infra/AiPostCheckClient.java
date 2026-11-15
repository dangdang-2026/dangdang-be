package com.dangdang.domain.post.infra;

import com.dangdang.global.config.property.AiProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiPostCheckClient implements PostCheckClient {

    private final AiProperties properties;
    private final RestClient restClient;

    public AiPostCheckClient(AiProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);  // 연결 2초
        factory.setReadTimeout(6_000);     // 응답 6초 (AI 서버의 GPT 확인 4초 + 여유)
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }

    // 실패하면 RestClientException이 그대로 나간다 → 서비스가 받아서 "검사 못 함"으로 처리
    @Override
    public Result check(String title, String content) {
        return restClient.post()
                .uri("/internal/post-check")
                .header("X-Internal-Token", properties.internalToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new Body(title == null ? "" : title, content))
                .retrieve()
                .body(Result.class);
    }

    private record Body(String title, String content) {}
}
