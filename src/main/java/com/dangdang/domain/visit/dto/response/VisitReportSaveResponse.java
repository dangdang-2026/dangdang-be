package com.dangdang.domain.visit.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;

// 리포트 저장 결과. created는 응답 상태(201 새로 저장 / 200 이미 있음)를 정하는 데만 쓰고 JSON에는 안 나감
public record VisitReportSaveResponse(
        Long id,
        @JsonIgnore boolean created) {
}
