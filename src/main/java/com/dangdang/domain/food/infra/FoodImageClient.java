package com.dangdang.domain.food.infra;

import java.util.List;

// 음식 사진 분석을 AI 서버에 맡기는 창구 (테스트에서는 가짜로 바꾼다)
public interface FoodImageClient {

    // 사진 한 장을 보내 음식 후보를 받음. AI 서버가 실패하면 RestClientException이 그대로 나감
    // requestId는 BE·AI 서버 로그를 맞춰 보는 번호, contentType은 사용자가 보낸 값 그대로 (없거나 깨진 값이어도 됨)
    Result candidates(String requestId, byte[] image, String contentType);

    // candidates = 사진 전체를 음식 하나로 본 후보 (예전 계약)
    // items = 한상 사진(F-13)에서 찾은 음식마다 위치와 후보 (새 계약). AI 서버가 아직 안 주면 null → 음식 하나로 처리
    record Result(List<Candidate> candidates, List<Item> items) {
        public Result(List<Candidate> candidates) {
            this(candidates, null);
        }
    }

    // name은 AI Hub 음식 이름 (식약처 foods.name과 다를 수 있음), confidence는 0~1
    record Candidate(String name, double confidence) {}

    // 사진 속 음식 하나
    record Item(Box box, List<Candidate> candidates) {}

    // 사진 기준 0~1 비율. x·y는 왼쪽 위 모서리
    record Box(double x, double y, double width, double height) {}
}
