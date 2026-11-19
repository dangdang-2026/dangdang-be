package com.dangdang.domain.visit.dto.response;

import com.dangdang.domain.visit.entity.VisitQuestion;

import java.time.Instant;

// 진료 질문 삭제 결과. 이미 지운 질문을 또 지우면 처음 지운 시각이 나감
public record VisitQuestionDeleteResponse(
        Long questionId,
        Instant deletedAt) {

    public static VisitQuestionDeleteResponse from(VisitQuestion q) {
        return new VisitQuestionDeleteResponse(q.getId(), q.getDeletedAt());
    }
}
