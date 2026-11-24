package com.dangdang.domain.caresite.infra;

// 심평원 병원 전체 목록을 한 쪽씩 받기 (DB 적재용). 테스트에서는 가짜로 바꿔 끼움
public interface HospitalListClient {

    // page는 1부터. 결과의 totalCount = 전국 전체 병원 수 (11/22 기준 79,867)
    HospitalSearchResult fetchPage(int page, int size);
}
