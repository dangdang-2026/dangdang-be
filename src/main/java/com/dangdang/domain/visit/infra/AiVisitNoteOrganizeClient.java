package com.dangdang.domain.visit.infra;

import com.dangdang.domain.visit.entity.VisitNoteExtract;
import com.dangdang.global.config.property.AiProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

@Component
public class AiVisitNoteOrganizeClient implements VisitNoteOrganizeClient {

    private final AiProperties properties;
    private final RestClient restClient;

    public AiVisitNoteOrganizeClient(AiProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);   // 연결 2초
        factory.setReadTimeout(20_000);     // 응답 20초 (gpt-5.4-mini가 근거 문장까지 옮기는 시간 + 여유, 명세 권장값)
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }

    // 사용자 쿠키는 보내지 않음. 응답은 공통 봉투 없이 바로 결과
    @Override
    public VisitNoteExtract organize(String requestId, String memo, LocalDate visitDate) {
        return restClient.post()
                .uri("/internal/visit-note/organize")
                .header("X-Internal-Token", properties.internalToken())
                .header("X-Request-Id", requestId)        // AI 서버 로그와 맞춰 보는 번호
                .contentType(MediaType.APPLICATION_JSON)
                .body(new Body(memo, visitDate))
                .retrieve()
                .body(VisitNoteExtract.class);
    }

    private record Body(String memo, LocalDate visitDate) {}
}
