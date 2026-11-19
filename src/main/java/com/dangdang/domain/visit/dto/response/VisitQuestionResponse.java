package com.dangdang.domain.visit.dto.response;

import com.dangdang.domain.visit.entity.VisitQuestion;
import com.dangdang.domain.visit.enums.QuestionSource;

import java.time.Instant;

// 진료 질문 하나
public record VisitQuestionResponse(
        Long questionId,
        String content,
        QuestionSource source,       // USER 직접 적음 / CHATBOT 챗봇에서 가져옴
        int sortOrder,
        Instant createdAt) {

    public static VisitQuestionResponse from(VisitQuestion q) {
        return new VisitQuestionResponse(q.getId(), q.getContent(), q.getSource(), q.getSortOrder(), q.getCreatedAt());
    }
}
