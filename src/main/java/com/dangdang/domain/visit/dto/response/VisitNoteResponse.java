package com.dangdang.domain.visit.dto.response;

import com.dangdang.domain.visit.entity.VisitNote;

import java.time.Instant;

// 진료 메모 하나
public record VisitNoteResponse(
        Long noteId,
        String content,              // 적은 그대로
        Instant createdAt,
        Instant updatedAt) {         // 고쳤는지 보려고 같이 내보냄 (안 고쳤으면 createdAt과 같음)

    public static VisitNoteResponse from(VisitNote n) {
        return new VisitNoteResponse(n.getId(), n.getContent(), n.getCreatedAt(), n.getUpdatedAt());
    }
}
