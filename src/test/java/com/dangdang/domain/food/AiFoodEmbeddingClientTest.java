package com.dangdang.domain.food;

import com.dangdang.domain.food.infra.AiFoodEmbeddingClient;
import com.dangdang.global.config.property.AiProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// AI 서버 /internal/embeddings 로 무엇을 보내고 어떻게 읽는지 (가짜 AI 서버를 잠깐 띄움, DB 필요 없음)
class AiFoodEmbeddingClientTest {

    HttpServer server;
    final AtomicReference<String> token = new AtomicReference<>();
    final AtomicReference<String> contentType = new AtomicReference<>();
    final AtomicReference<String> body = new AtomicReference<>();
    final AtomicReference<String> response = new AtomicReference<>(
            "{\"model\":\"text-embedding-3-small\",\"dim\":3,\"vectors\":[[0.1,0.2,0.3],[0.4,0.5,0.6]]}");
    AiFoodEmbeddingClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/embeddings", exchange -> {
            token.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] res = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, res.length);
            exchange.getResponseBody().write(res);
            exchange.close();
        });
        server.start();
        client = new AiFoodEmbeddingClient(
                new AiProperties("http://127.0.0.1:" + server.getAddress().getPort(), "test-token"));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void 이름들을_texts로_보내고_보낸_순서대로_벡터를_받는다() {
        List<float[]> vectors = client.embed(List.of("볶음밥 김치", "달걀찜"));

        assertThat(token.get()).isEqualTo("test-token");
        assertThat(contentType.get()).startsWith("application/json");
        assertThat(body.get()).isEqualTo("{\"texts\":[\"볶음밥 김치\",\"달걀찜\"]}");
        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(vectors.get(1)).containsExactly(0.4f, 0.5f, 0.6f);
    }

    @Test
    void 벡터_수가_보낸_이름_수와_다르면_예외() {
        response.set("{\"model\":\"m\",\"dim\":3,\"vectors\":[[0.1,0.2,0.3]]}");

        assertThatThrownBy(() -> client.embed(List.of("가", "나")))
                .isInstanceOf(RestClientException.class);
    }
}
