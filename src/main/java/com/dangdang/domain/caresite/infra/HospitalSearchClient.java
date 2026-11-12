package com.dangdang.domain.caresite.infra;

// 병원 이름 -> 심평원에 등록된 병원 목록 (테스트에서는 가짜로 바꿔 끼움)
public interface HospitalSearchClient {

    // page는 1부터 (심평원 규칙 그대로)
    HospitalSearchResult search(String keyword, int page, int size);
}
