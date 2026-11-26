package com.dangdang.domain.admin.controller;

import com.dangdang.domain.admin.dto.response.AdminDashboardResponse;
import com.dangdang.domain.admin.enums.DashboardPeriod;
import com.dangdang.domain.admin.service.AdminDashboardService;
import com.dangdang.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// /api/admin/** 는 SecurityConfig에서 ADMIN만 들어온다
@Tag(name = "Admin · Dashboard", description = "운영자 대시보드 API")
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @Operation(
            summary = "[ 운영자 | 토큰 O(ADMIN) | 대시보드 ]",
            description =
                    """
                    **Parameters** period(TODAY · WEEK 기본 · MONTH) — 오늘을 포함해 1일 · 7일 · 30일
                    **Returns** todo(처리할 신고·걱정되는 글·오늘 풀리는 제한·이의 신청·예약 소식, 지금 기준), summary(기간 합계), daily(날짜별, 한국 날짜, 0인 날도 채움). ADMIN이 아니면 403
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<AdminDashboardResponse>> get(
            @RequestParam(defaultValue = "WEEK") DashboardPeriod period) {
        return ResponseEntity.ok(BaseResponse.success(200, "대시보드 조회에 성공했습니다.",
                adminDashboardService.get(period)));
    }
}
