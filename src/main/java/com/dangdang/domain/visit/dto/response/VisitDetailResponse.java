package com.dangdang.domain.visit.dto.response;

import com.dangdang.domain.visit.entity.Visit;

import java.time.Instant;
import java.util.List;

// 진료 일정 상세 = 일정 + 질문 + 메모
// 메모는 다음 작업에서 채움 (지금은 항상 [])
public record VisitDetailResponse(
        Long visitId,
        Long careSiteId,
        Instant visitAt,
        Instant createdAt,
        Instant updatedAt,
        List<VisitQuestionResponse> questions,   // 정렬 순서 → 먼저 만든 순, 지운 질문 빠짐
        List<Object> notes) {

    public static VisitDetailResponse of(Visit v, List<VisitQuestionResponse> questions) {
        return new VisitDetailResponse(v.getId(), v.getCareSiteId(), v.getVisitAt(),
                v.getCreatedAt(), v.getUpdatedAt(), questions, List.of());
    }
}
