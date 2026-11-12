package com.dangdang.domain.caresite.infra;

import java.util.List;

// 심평원 검색 결과 한 페이지
// totalCount = 검색어에 걸린 전체 병원 수 (이 페이지 개수 아님)
public record HospitalSearchResult(List<Hospital> hospitals, int totalCount) {

    // 병원 하나 (우리가 쓰는 칸만)
    public record Hospital(
            String hiraCode,   // ykiho: 암호화된 요양기호
            String name,       // yadmNm
            String category,   // clCdNm: 상급종합·종합병원·병원·의원 등
            String address,    // addr
            String phone) {    // telno
    }
}
