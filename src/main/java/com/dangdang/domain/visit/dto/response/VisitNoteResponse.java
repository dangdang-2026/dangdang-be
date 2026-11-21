package com.dangdang.domain.visit.dto.response;

import com.dangdang.domain.visit.entity.VisitNote;
import com.dangdang.domain.visit.entity.VisitNoteExtract;

import java.time.Instant;

// 진료 메모 하나
public record VisitNoteResponse(
        Long noteId,
        String content,              // 적은 그대로
        VisitNoteExtract aiExtract,  // AI 정리 결과 (정리한 적 없거나, 정리한 뒤 메모를 고쳤으면 null)
        Instant createdAt,
        Instant updatedAt) {         // 고쳤는지 보려고 같이 내보냄 (안 고쳤으면 createdAt과 같음)

    public static VisitNoteResponse from(VisitNote n) {
        return new VisitNoteResponse(n.getId(), n.getContent(), n.getAiExtract(), n.getCreatedAt(), n.getUpdatedAt());
    }
}
