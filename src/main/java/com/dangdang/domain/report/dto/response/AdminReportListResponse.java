package com.dangdang.domain.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// items는 최대 100줄, totalCount는 전체 묶음 수 (100을 넘을 수 있다)
public record AdminReportListResponse(
        List<AdminReportItemResponse> items,
        @Schema(description = "전체 묶음 수 (items는 100줄까지)", example = "3") long totalCount) {
}
