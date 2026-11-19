package com.dangdang.domain.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

// 신고 처리함 한 줄
public record AdminReportItemResponse(
        @Schema(example = "POST") String targetType,
        @Schema(example = "12") String targetId,
        @Schema(description = "글 번호 (댓글이면 그 댓글이 달린 글)", example = "12") String postId,
        @Schema(description = "제목 또는 댓글 앞부분 (40자)", example = "이 약 두 알 먹으면 괜찮아요") String preview,
        @Schema(description = "VISIBLE · HIDDEN · DELETED", example = "VISIBLE") String state,
        @Schema(example = "3") long reportCount,
        @Schema(example = "[\"MEDICAL_ADVICE\"]") List<String> reasons,
        Instant lastReportedAt) {
}
