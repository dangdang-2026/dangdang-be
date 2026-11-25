package com.dangdang.domain.report.controller;

import com.dangdang.domain.report.dto.request.AdminHideRequest;
import com.dangdang.domain.report.dto.request.AdminMemoRequest;
import com.dangdang.domain.report.dto.response.AdminReportActionResponse;
import com.dangdang.domain.report.dto.response.AdminReportDetailResponse;
import com.dangdang.domain.report.dto.response.AdminReportListResponse;
import com.dangdang.domain.report.enums.ReportStatus;
import com.dangdang.domain.report.enums.ReportTargetType;
import com.dangdang.domain.report.service.AdminReportService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// /api/admin/** 는 SecurityConfig에서 ADMIN만 들어온다
@Tag(name = "Admin · Report", description = "운영자 신고 처리함 API")
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminReportService adminReportService;

    @Operation(
            summary = "[ 운영자 | 토큰 O(ADMIN) | 신고 처리함 ]",
            description =
                    """
                    **Parameters** status(RECEIVED 기본 · HIDDEN · DISMISSED)
                    **Returns** 대상(글·댓글)별로 묶은 신고. 사람의 위급(CRISIS) 신고 → 자동 위급 신고 → 사람 신고 많은 순 → 최근 순, 최대 100줄. reportCount는 사람 신고만, 자동 신고는 systemReportCount. ADMIN이 아니면 403
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<AdminReportListResponse>> list(
            @RequestParam(defaultValue = "RECEIVED") ReportStatus status) {
        return ResponseEntity.ok(BaseResponse.success(200, "신고 처리함 조회에 성공했습니다.",
                adminReportService.list(status)));
    }

    @Operation(
            summary = "[ 운영자 | 토큰 O(ADMIN) | 원문 보기 ]",
            description =
                    """
                    **Parameters** targetType(POST·COMMENT), targetId
                    **Returns** 원문(가린 글·지운 글도) · 글쓴이 회원 번호(이용 제한용) · 신고 목록(신고한 사람은 안 나옴. source USER·SYSTEM, 자동 신고는 detail 「올리기 전 안내를 보고도 올림 (자동: DOSE)」) · 처리 기록. 없으면 REPORT_40401
                    """)
    @GetMapping("/{targetType}/{targetId}")
    public ResponseEntity<BaseResponse<AdminReportDetailResponse>> detail(
            @PathVariable ReportTargetType targetType, @PathVariable Long targetId) {
        return ResponseEntity.ok(BaseResponse.success(200, "원문 조회에 성공했습니다.",
                adminReportService.detail(targetType, targetId)));
    }

    @Operation(
            summary = "[ 운영자 | 토큰 O(ADMIN) | 가리기 ]",
            description =
                    """
                    **Parameters** reason(CRISIS를 뺀 신고 사유 7개 중 하나 → U-14 문구, CRISIS면 GLOBAL_40001), memo(200자, 운영자만 봄)
                    **Returns** state=HIDDEN, 처리된 신고 수. 이미 가렸으면 REPORT_40901
                    """)
    @PostMapping("/{targetType}/{targetId}/hide")
    public ResponseEntity<BaseResponse<AdminReportActionResponse>> hide(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable ReportTargetType targetType, @PathVariable Long targetId,
            @Valid @RequestBody AdminHideRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "가리기에 성공했습니다.",
                adminReportService.hide(principal.userId(), targetType, targetId, request)));
    }

    @Operation(
            summary = "[ 운영자 | 토큰 O(ADMIN) | 문제없음 ]",
            description = "**Parameters** memo(선택) · **Returns** 대상은 그대로, 접수된 신고만 DISMISSED. 처리할 접수 신고가 없으면 REPORT_40903")
    @PostMapping("/{targetType}/{targetId}/dismiss")
    public ResponseEntity<BaseResponse<AdminReportActionResponse>> dismiss(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable ReportTargetType targetType, @PathVariable Long targetId,
            @Valid @RequestBody(required = false) AdminMemoRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "문제없음 처리에 성공했습니다.",
                adminReportService.dismiss(principal.userId(), targetType, targetId, memoOf(request))));
    }

    @Operation(
            summary = "[ 운영자 | 토큰 O(ADMIN) | 되돌리기 (이의 신청) ]",
            description = "**Parameters** memo(선택) · **Returns** state=VISIBLE. 가려져 있지 않으면 REPORT_40902")
    @PostMapping("/{targetType}/{targetId}/unhide")
    public ResponseEntity<BaseResponse<AdminReportActionResponse>> unhide(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable ReportTargetType targetType, @PathVariable Long targetId,
            @Valid @RequestBody(required = false) AdminMemoRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "되돌리기에 성공했습니다.",
                adminReportService.unhide(principal.userId(), targetType, targetId, memoOf(request))));
    }

    private String memoOf(AdminMemoRequest request) {
        return request == null ? null : request.memo();
    }
}
