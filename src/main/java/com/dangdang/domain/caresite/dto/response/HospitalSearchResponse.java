package com.dangdang.domain.caresite.dto.response;

import com.dangdang.domain.caresite.infra.HospitalSearchResult;

import java.util.List;

// 병원 검색 응답 한 페이지. 고른 병원의 칸을 그대로 병원 등록(POST /api/care-sites)에 넣으면 됨
public record HospitalSearchResponse(
        List<Item> items,
        int page,            // 0부터
        int size,
        int totalCount,      // 검색어에 걸린 전체 병원 수
        boolean hasNext) {

    public record Item(
            String hiraCode,   // 등록할 때 hiraCode로 그대로 보냄
            String name,
            String category,   // 상급종합·종합병원·병원·의원 등 (보여 주기용, 저장 안 함)
            String address,
            String phone) {
    }

    public static HospitalSearchResponse of(HospitalSearchResult result, int page, int size) {
        List<Item> items = result.hospitals().stream()
                .map(h -> new Item(h.hiraCode(), h.name(), h.category(), h.address(), h.phone()))
                .toList();
        boolean hasNext = (long) (page + 1) * size < result.totalCount();
        return new HospitalSearchResponse(items, page, size, result.totalCount(), hasNext);
    }
}
