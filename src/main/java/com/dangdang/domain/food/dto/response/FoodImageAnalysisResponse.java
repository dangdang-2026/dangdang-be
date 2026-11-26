package com.dangdang.domain.food.dto.response;

import java.util.Comparator;
import java.util.List;

// 음식 사진 AI 분석 결과
// fallback = true 면 후보 화면 대신 「이름으로 적기」로 넘어감 (AI가 확신이 낮거나 AI 서버가 실패함). 이때 candidates = []
// items = 한상 사진(F-13)에서 찾은 음식마다 위치·후보. 예전 화면은 fallback·candidates만 보면 됨 (가장 확신 높은 음식 기준)
public record FoodImageAnalysisResponse(
        boolean fallback,
        List<Candidate> candidates,   // 확신도 높은 순 (최대 3개)
        List<Item> items) {           // 사진 속 음식들 (AI가 위치를 안 줬으면 위치 없는 한 개). AI 서버가 실패하면 []

    // name·confidence는 AI가 준 그대로, foods는 그 이름으로 음식 검색한 앞쪽 결과 (못 찾으면 [])
    public record Candidate(String name, double confidence, List<FoodResponse> foods) {}

    // 사진 속 음식 하나. box·point가 null이면 위치를 모름 (AI가 사진 전체를 음식 하나로 봄)
    // point = box의 가운데 (프론트 DetectedFood.point에 그대로 씀, 사진 위 번호 표시)
    // fallback = true 면 이 음식만 「이름으로 적기」 (candidates = [])
    public record Item(Box box, Point point, boolean fallback, List<Candidate> candidates) {

        public static Item of(Box box, boolean fallback, List<Candidate> candidates) {
            Point point = box == null ? null : new Point(box.x() + box.width() / 2, box.y() + box.height() / 2);
            return new Item(box, point, fallback, candidates);
        }
    }

    // 사진 기준 0~1 비율. x·y는 왼쪽 위 모서리
    public record Box(double x, double y, double width, double height) {}

    // 사진 기준 0~1 비율의 한 점
    public record Point(double x, double y) {}

    public static FoodImageAnalysisResponse fallbackToName() {
        return new FoodImageAnalysisResponse(true, List.of(), List.of());
    }

    // 예전 칸(fallback·candidates)은 음식 중 1등 확신도가 가장 높은 것으로 채움 → 지금 화면이 그대로 동작
    public static FoodImageAnalysisResponse of(List<Item> items) {
        Item best = items.stream()
                .filter(i -> !i.fallback() && !i.candidates().isEmpty())   // 후보가 빈 Item이 와도 get(0)에서 터지지 않게
                .max(Comparator.comparingDouble(i -> i.candidates().get(0).confidence()))
                .orElse(null);
        return best == null
                ? new FoodImageAnalysisResponse(true, List.of(), items)
                : new FoodImageAnalysisResponse(false, best.candidates(), items);
    }
}
