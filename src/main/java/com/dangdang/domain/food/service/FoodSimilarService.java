package com.dangdang.domain.food.service;

import com.dangdang.domain.food.dto.response.FoodResponse;
import com.dangdang.domain.food.entity.Food;
import com.dangdang.domain.food.infra.FoodEmbeddingClient;
import com.dangdang.domain.food.repository.FoodEmbeddingRepository;
import com.dangdang.domain.food.repository.FoodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

// 사진 분석 후보가 글자 검색으로 0건일 때만 부른다. 이름 뜻이 가까운 식약처 음식
// AI 서버가 안 되면 빈 목록 (임베딩 때문에 사진 분석 전체가 실패하지 않게)
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FoodSimilarService {

    // 코사인 거리 기준 (#189). 개발 서버 DB로 재 보니 0.21~0.31에 같은 음식과 다른 음식이 섞여 있음
    //  - 맞음: 떡국 만두국과 떡국 0.18, 도라지무침과 도라지나물무침 0.19, 곰탕 설렁탕과 곰탕 0.19
    //  - 틀림: 감자전과 감자조림 0.22, 고추장진미채볶음과 고추장볶음 0.24, 소세지볶음과 소세지빵 0.31
    //  엉뚱한 음식이 붙는 것보다 빈칸이 나아서, 틀린 짝이 하나도 안 들어오는 0.20
    static final double MAX_DISTANCE = 0.20;
    static final int DIM = 1536;               // foods.name_embedding vector(1536). 다르면 DB가 거절해서 미리 거름

    private final FoodEmbeddingClient client;
    private final FoodEmbeddingRepository embeddingRepository;
    private final FoodRepository foodRepository;

    public List<FoodResponse> similar(String name, int limit) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        List<float[]> vectors;
        try {
            vectors = client.embed(List.of(name.strip()));
        } catch (RestClientException e) {
            log.warn("[FoodSimilar] AI 서버 실패, 빈 목록 {}", e.getClass().getSimpleName());
            return List.of();
        }
        if (vectors == null || vectors.size() != 1 || vectors.get(0).length != DIM) {
            return List.of();
        }
        List<String> codes = embeddingRepository.nearestCodes(vectors.get(0), MAX_DISTANCE, limit);
        Map<String, Food> byCode = foodRepository.findAllById(codes).stream()
                .collect(Collectors.toMap(Food::getFoodCode, Function.identity()));
        return codes.stream().map(byCode::get).filter(Objects::nonNull).map(FoodResponse::from).toList();
    }
}
