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
        @Schema(description = "사람이 한 신고 수 (자동 신고는 안 셈)", example = "3") long reportCount,
        @Schema(description = "자동 신고 수 — 글쓴이가 올리기 전 안내를 보고도 올린 글이면 1 이상", example = "0") long systemReportCount,
        @Schema(example = "[\"MEDICAL_ADVICE\"]") List<String> reasons,
        Instant lastReportedAt) {
}
