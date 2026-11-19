package com.dangdang.domain.food.dto.response;

import java.util.List;

// 음식 사진 AI 분석 결과
// fallback = true 면 후보 화면 대신 「이름으로 적기」로 넘어감 (AI가 확신이 낮거나 AI 서버가 실패함). 이때 candidates = []
public record FoodImageAnalysisResponse(
        boolean fallback,
        List<Candidate> candidates) {   // 확신도 높은 순 (최대 3개)

    // name·confidence는 AI가 준 그대로, foods는 그 이름으로 음식 검색한 앞쪽 결과 (못 찾으면 [])
    public record Candidate(String name, double confidence, List<FoodResponse> foods) {}

    public static FoodImageAnalysisResponse fallbackToName() {
        return new FoodImageAnalysisResponse(true, List.of());
    }

    public static FoodImageAnalysisResponse of(List<Candidate> candidates) {
        return new FoodImageAnalysisResponse(false, candidates);
    }
}
