package com.dangdang.domain.food;

import com.dangdang.domain.food.infra.AiFoodImageClient;
import com.dangdang.domain.food.infra.FoodImageClient;
import com.dangdang.global.config.property.AiProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

// AI 서버로 실제로 무엇을 보내는지 확인 (가짜 AI 서버를 이 컴퓨터에 잠깐 띄움, DB 필요 없음)
class AiFoodImageClientTest {

    HttpServer server;
    final AtomicReference<String> token = new AtomicReference<>();
    final AtomicReference<String> requestId = new AtomicReference<>();
    final AtomicReference<String> contentType = new AtomicReference<>();
    final AtomicReference<String> body = new AtomicReference<>();
    AiFoodImageClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/food-candidates", exchange -> {
            token.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            requestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            byte[] res = "{\"candidates\":[{\"name\":\"김밥\",\"confidence\":0.9972}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, res.length);
            exchange.getResponseBody().write(res);
            exchange.close();
        });
        server.start();
        client = new AiFoodImageClient(
                new AiProperties("http://127.0.0.1:" + server.getAddress().getPort(), "test-token"));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void 사진을_image_필드에_고정_파일_이름으로_보내고_토큰을_붙인다() {
        FoodImageClient.Result result = client.candidates("req-1", new byte[]{1, 2, 3}, "image/jpeg");

        assertThat(token.get()).isEqualTo("test-token");
        assertThat(requestId.get()).isEqualTo("req-1");
        assertThat(contentType.get()).startsWith("multipart/form-data");
        assertThat(body.get())
                .contains("name=\"image\"")
                .contains("filename=\"image.jpg\"")
                .contains("Content-Type: image/jpeg");
        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().get(0).name()).isEqualTo("김밥");
        assertThat(result.candidates().get(0).confidence()).isEqualTo(0.9972);
    }

    @Test
    void 형식_값이_깨졌거나_없으면_그냥_파일로_보낸다() {
        client.candidates("req-1", new byte[]{1}, "jpeg;;");
        assertThat(body.get()).contains("Content-Type: application/octet-stream");

        client.candidates("req-1", new byte[]{1}, null);
        assertThat(body.get()).contains("Content-Type: application/octet-stream");
    }
}
