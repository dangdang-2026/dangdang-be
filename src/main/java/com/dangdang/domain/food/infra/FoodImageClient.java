package com.dangdang.domain.food.infra;

import java.util.List;

// 음식 사진 분석을 AI 서버에 맡기는 창구 (테스트에서는 가짜로 바꾼다)
public interface FoodImageClient {

    // 사진 한 장을 보내 음식 후보를 받음. AI 서버가 실패하면 RestClientException이 그대로 나감
    // contentType은 사용자가 보낸 값 그대로 (없거나 깨진 값이어도 됨)
    Result candidates(byte[] image, String contentType);

    record Result(List<Candidate> candidates) {}

    // name은 AI Hub 음식 이름 (식약처 foods.name과 다를 수 있음), confidence는 0~1
    record Candidate(String name, double confidence) {}
}
