package com.dangdang.domain.food.service;

import com.dangdang.domain.food.infra.FoodEmbeddingClient;
import com.dangdang.domain.food.repository.FoodEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.List;

// 서버가 뜨면 벡터가 빈 음식 이름을 100개씩 AI 서버에 보내 채운다. 다 차 있으면 조회 한 번으로 끝
// 스케줄러 스레드(알림, SSE ping 몫)를 잡지 않게 가상 스레드에서 돈다
// food-embedding.fill-enabled=true 인 서버에서만 생긴다 (로컬과 테스트는 안 생김)
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "food-embedding.fill-enabled", havingValue = "true")
public class FoodEmbeddingFiller {

    static final int BATCH = 100;                 // AI 서버가 한 번에 받는 최대 개수
    private static final int MAX_ROUNDS = 500;    // 5만 개. 혹시 끝이 안 나도 여기서 멈춤

    private final FoodEmbeddingRepository repository;
    private final FoodEmbeddingClient client;

    @EventListener(ApplicationReadyEvent.class)
    public void fillInBackground() {
        Thread.ofVirtual().name("food-embedding-fill").start(this::fill);
    }

    // 채운 이름 수를 돌려줌 (테스트)
    public int fill() {
        int filled = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<String> names = repository.namesWithoutEmbedding(BATCH);
            if (names.isEmpty()) {
                break;
            }
            List<float[]> vectors;
            try {
                // "볶음밥_김치"는 "볶음밥 김치"로 보낸다 (밑줄은 뜻이 없어서)
                vectors = client.embed(names.stream().map(n -> n.replace('_', ' ')).toList());
            } catch (RestClientException e) {
                log.warn("[FoodEmbedding] AI 서버 실패, 다음 배포 때 이어서 채움 {}", e.getClass().getSimpleName());
                return filled;
            }
            if (vectors.size() != names.size()) {
                log.warn("[FoodEmbedding] 벡터 수가 이름 수와 달라서 멈춤 {}/{}", vectors.size(), names.size());
                return filled;
            }
            for (int i = 0; i < names.size(); i++) {
                repository.save(names.get(i), vectors.get(i));
            }
            filled += names.size();
        }
        if (filled > 0) {
            log.info("[FoodEmbedding] 이름 {}개 채움", filled);
        }
        return filled;
    }
}
