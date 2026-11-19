package com.dangdang.domain.visit.dto.response;

import com.dangdang.domain.visit.entity.Visit;
import org.springframework.data.domain.Slice;

import java.util.List;

// 진료 일정 목록 한 페이지 (전체 개수는 안 셈, 다음 페이지가 있는지만)
public record VisitPageResponse(
        List<VisitResponse> items,   // 결과가 없으면 []
        int page,                    // 0부터
        int size,
        boolean hasNext) {

    public static VisitPageResponse from(Slice<Visit> slice) {
        return new VisitPageResponse(
                slice.getContent().stream().map(VisitResponse::from).toList(),
                slice.getNumber(), slice.getSize(), slice.hasNext());
    }
}
