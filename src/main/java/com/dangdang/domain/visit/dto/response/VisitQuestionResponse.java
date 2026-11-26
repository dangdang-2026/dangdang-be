package com.dangdang.domain.visit.dto.response;

import com.dangdang.domain.card.entity.ContentCard;
import com.dangdang.domain.visit.entity.VisitQuestion;
import com.dangdang.domain.visit.enums.QuestionSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

// 진료 질문 하나
public record VisitQuestionResponse(
        Long questionId,
        String content,
        QuestionSource source,       // USER 직접 적음 / CHATBOT 챗봇에서 가져옴 / CARD 카드뉴스에서 담음
        int sortOrder,
        Instant createdAt,
        @Schema(description = "카드뉴스에서 담았으면 그 카드 번호 (U-28). 아니면 null", example = "12")
        Long sourceCardId,
        @Schema(description = "출처 카드가 내려갔으면 true → 「출처 카드가 내려갔어요」 (U-26c). CARD가 아니면 false")
        boolean sourceCardWithdrawn) {

    // 카드 상태를 읽으니 트랜잭션 안에서 부른다 (질문 서비스·일정 서비스 둘 다 클래스에 readOnly 트랜잭션)
    public static VisitQuestionResponse from(VisitQuestion q) {
        ContentCard card = q.getSourceCard();
        return new VisitQuestionResponse(q.getId(), q.getContent(), q.getSource(), q.getSortOrder(), q.getCreatedAt(),
                card == null ? null : card.getId(), q.isSourceCardWithdrawn());
    }
}
