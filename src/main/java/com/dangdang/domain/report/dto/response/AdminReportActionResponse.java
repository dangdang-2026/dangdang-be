package com.dangdang.domain.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminReportActionResponse(
        String targetType,
        String targetId,
        @Schema(description = "처리 뒤 상태 VISIBLE · HIDDEN · DELETED") String state,
        @Schema(description = "이번에 처리된 접수 신고 수", example = "3") int handledReports) {
}
