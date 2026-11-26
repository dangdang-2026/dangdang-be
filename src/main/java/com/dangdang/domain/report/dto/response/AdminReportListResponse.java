package com.dangdang.domain.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// items는 최대 100줄, totalCount는 전체 묶음 수 (100을 넘을 수 있다)
public record AdminReportListResponse(
        List<AdminReportItemResponse> items,
        @Schema(description = "전체 묶음 수 (items는 100줄까지)", example = "3") long totalCount,
        @Schema(description = "탭 숫자. 고른 탭과 상관없이 세 탭 모두") Counts counts) {

    // 탭마다 묶음(글·댓글) 수. 같은 글이 처리 전과 문제없음에 둘 다 있으면 양쪽에 센다 (목록과 같은 기준)
    public record Counts(long received, long hidden, long dismissed) {
    }
}
