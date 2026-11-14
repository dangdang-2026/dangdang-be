package com.dangdang.domain.report.controller;

import com.dangdang.domain.report.dto.request.ReportCreateRequest;
import com.dangdang.domain.report.dto.response.ReportResponse;
import com.dangdang.domain.report.service.ReportService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Report", description = "커뮤니티 신고 API")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 신고 ]",
            description =
                    """
                    **Parameters** targetType(POST·COMMENT), targetId, reason, detail(ETC일 때, 200자)
                    **Returns** reported=true. 같은 대상을 다시 신고해도 200. 내 글 REPORT_40001, 대상 없음 POST_40401·COMMENT_40401
                    """)
    @PostMapping
    public ResponseEntity<BaseResponse<ReportResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody ReportCreateRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "신고에 성공했습니다.",
                reportService.create(principal.userId(), request)));
    }
}
