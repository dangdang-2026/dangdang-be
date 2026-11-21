package com.dangdang.domain.visit;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.visit.entity.VisitNoteExtract;
import com.dangdang.domain.visit.enums.ExtractStatus;
import com.dangdang.domain.visit.infra.AiVisitNoteOrganizeClient;
import com.dangdang.global.config.property.AiProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

// AI 서버로 무엇을 보내고, 명세의 응답 모양을 제대로 읽는지 확인 (가짜 AI 서버를 잠깐 띄움, DB 필요 없음)
class AiVisitNoteOrganizeClientTest {

    // 노션 명세 「진료 메모 정리 (내부 · AI 서버)」의 응답 예시 그대로 (+ 모르는 칸 하나)
    static final String AI_RESPONSE = """
            {
              "measureSlots": { "value": ["FASTING", "BREAKFAST_AFTER", "LUNCH_AFTER", "DINNER_AFTER"], "evidence": "…세 끼 식후 1시간에 재래요.", "status": "FOUND" },
              "fastingTarget": { "value": 95, "evidence": "공복은 95 미만, 식후는 140 미만으로 보자고 하셨어요.", "status": "FOUND" },
              "postMealTarget": { "value": 140, "evidence": "…", "status": "FOUND" },
              "postMealMinutes": null,
              "nextVisitDate": { "value": "2026-11-27", "evidence": "다음 진료는 2주 뒤 금요일이에요.", "status": "CHECK_NEEDED" },
              "insulinSkipped": true,
              "promptVersion": "visit-note-v1",
              "somethingNew": 1
            }
            """;

    HttpServer server;
    final AtomicReference<String> token = new AtomicReference<>();
    final AtomicReference<String> requestId = new AtomicReference<>();
    final AtomicReference<String> body = new AtomicReference<>();
    final AtomicReference<String> response = new AtomicReference<>(AI_RESPONSE);
    AiVisitNoteOrganizeClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/visit-note/organize", exchange -> {
            token.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            requestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] res = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, res.length);
            exchange.getResponseBody().write(res);
            exchange.close();
        });
        server.start();
        client = new AiVisitNoteOrganizeClient(
                new AiProperties("http://127.0.0.1:" + server.getAddress().getPort(), "test-token"));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void 메모와_진료일을_보내고_명세_모양의_응답을_읽는다() {
        VisitNoteExtract result = client.organize("req-1", "공복은 95 미만", LocalDate.of(2026, 11, 14));

        assertThat(token.get()).isEqualTo("test-token");
        assertThat(requestId.get()).isEqualTo("req-1");
        assertThat(body.get()).contains("\"memo\":\"공복은 95 미만\"").contains("\"visitDate\":\"2026-11-14\"");

        assertThat(result.measureSlots().value()).containsExactly(
                MeasureSlot.FASTING, MeasureSlot.BREAKFAST_AFTER, MeasureSlot.LUNCH_AFTER, MeasureSlot.DINNER_AFTER);
        assertThat(result.fastingTarget().value()).isEqualTo(95);
        assertThat(result.fastingTarget().status()).isEqualTo(ExtractStatus.FOUND);
        assertThat(result.postMealMinutes()).isNull();
        assertThat(result.nextVisitDate().value()).isEqualTo(LocalDate.of(2026, 11, 27));
        assertThat(result.nextVisitDate().status()).isEqualTo(ExtractStatus.CHECK_NEEDED);
        assertThat(result.insulinSkipped()).isTrue();
        assertThat(result.promptVersion()).isEqualTo("visit-note-v1");
    }

    @Test
    void 처음_보는_상태나_잴_때가_와도_응답을_읽고_그_값만_null로() {
        // AI 서버가 새 값을 늘려도 정리 전체가 실패하지 않게 (정리할 때 「확인 필요」로 바뀜)
        response.set("""
                {
                  "measureSlots": { "value": ["FASTING", "LUNCH_BEFORE"], "evidence": "공복이랑 점심 전", "status": "FOUND" },
                  "fastingTarget": { "value": 95, "evidence": "공복은 95 미만", "status": "NOT_SURE" },
                  "insulinSkipped": false,
                  "promptVersion": "visit-note-v2"
                }
                """);

        VisitNoteExtract result = client.organize("req-2", "공복은 95 미만", LocalDate.of(2026, 11, 14));

        assertThat(result.measureSlots().value()).containsExactly(MeasureSlot.FASTING, null);
        assertThat(result.fastingTarget().value()).isEqualTo(95);
        assertThat(result.fastingTarget().status()).isNull();
    }
}
