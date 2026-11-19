package com.dangdang.domain.visit.dto.response;

import com.dangdang.domain.visit.entity.VisitNoteExtract;

// 진료 메모 AI 정리 결과
// aiExtract의 칸마다 {value, evidence(근거 문장), status(FOUND/CHECK_NEEDED)}, 메모에 없는 칸은 null
public record VisitNoteOrganizeResponse(
        Long noteId,
        VisitNoteExtract aiExtract) {
}
