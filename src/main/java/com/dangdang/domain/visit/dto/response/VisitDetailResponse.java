package com.dangdang.domain.visit.dto.response;

import com.dangdang.domain.visit.entity.Visit;

import java.time.Instant;
import java.util.List;

// 진료 일정 상세 = 일정 + 질문 + 메모
// 질문·메모는 다음 작업에서 채움 (지금은 항상 [])
public record VisitDetailResponse(
        Long visitId,
        Long careSiteId,
        Instant visitAt,
        Instant createdAt,
        Instant updatedAt,
        List<Object> questions,
        List<Object> notes) {

    public static VisitDetailResponse from(Visit v) {
        return new VisitDetailResponse(v.getId(), v.getCareSiteId(), v.getVisitAt(),
                v.getCreatedAt(), v.getUpdatedAt(), List.of(), List.of());
    }
}
