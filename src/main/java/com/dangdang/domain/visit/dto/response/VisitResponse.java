package com.dangdang.domain.visit.dto.response;

import com.dangdang.domain.visit.entity.Visit;

import java.time.Instant;

// 진료 일정 하나 (목록·등록·수정 응답)
public record VisitResponse(
        Long visitId,
        Long careSiteId,             // 없으면 null
        Instant visitAt,             // 날짜 미정이면 null
        Instant createdAt,
        Instant updatedAt) {

    public static VisitResponse from(Visit v) {
        return new VisitResponse(v.getId(), v.getCareSiteId(), v.getVisitAt(), v.getCreatedAt(), v.getUpdatedAt());
    }
}
