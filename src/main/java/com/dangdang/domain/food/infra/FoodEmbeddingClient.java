package com.dangdang.domain.food.infra;

import java.util.List;

// 음식 이름을 AI 서버에서 벡터로 바꾸는 창구 (테스트에서는 가짜로 바꾼다)
public interface FoodEmbeddingClient {

    // 이름 1~100개를 보내면 같은 순서로 1536차원 벡터를 돌려준다
    // AI 서버가 실패하면 RestClientException이 그대로 나간다
    List<float[]> embed(List<String> texts);
}
