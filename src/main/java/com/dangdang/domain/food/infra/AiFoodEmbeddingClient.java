package com.dangdang.domain.food.infra;

import com.dangdang.global.config.property.AiProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class AiFoodEmbeddingClient implements FoodEmbeddingClient {

    private final AiProperties properties;
    private final RestClient restClient;

    public AiFoodEmbeddingClient(AiProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);   // 연결 2초
        factory.setReadTimeout(20_000);     // AI 서버가 OpenAI를 15초까지 기다리니 그보다 길게
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }

    record Request(List<String> texts) {}

    record Response(String model, int dim, List<List<Double>> vectors) {}

    // AI 서버 /internal/embeddings 에 이름들을 보내고, 보낸 순서 그대로 벡터를 받는다
    @Override
    public List<float[]> embed(List<String> texts) {
        Response res = restClient.post()
                .uri("/internal/embeddings")
                .header("X-Internal-Token", properties.internalToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new Request(texts))
                .retrieve()
                .body(Response.class);
        if (res == null || res.vectors() == null || res.vectors().size() != texts.size()) {
            throw new RestClientException("임베딩 개수가 보낸 이름 수와 다름");
        }
        return res.vectors().stream().map(AiFoodEmbeddingClient::toFloats).toList();
    }

    private static float[] toFloats(List<Double> v) {
        float[] out = new float[v.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = v.get(i).floatValue();
        }
        return out;
    }
}
