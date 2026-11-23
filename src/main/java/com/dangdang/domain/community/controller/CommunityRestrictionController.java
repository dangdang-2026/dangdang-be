package com.dangdang.domain.community.controller;

import com.dangdang.domain.community.dto.request.AppealRequest;
import com.dangdang.domain.community.dto.request.SanctionCreateRequest;
import com.dangdang.domain.community.dto.response.AppealResponse;
import com.dangdang.domain.community.dto.response.RestrictionResponse;
import com.dangdang.domain.community.dto.response.SanctionResponse;
import com.dangdang.domain.community.service.SanctionService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Community Restriction", description = "커뮤니티 이용 제한 API")
@RestController
@RequiredArgsConstructor
public class CommunityRestrictionController {

    private final SanctionService sanctionService;

    @Operation(summary = "[ 사용자 | 토큰 O | 내 이용 제한 (U-17) ]",
            description = "**Returns** restricted · reason · endsAt(null = 운영자가 풀 때까지) · appealed(이의 신청했는지). 제한 중이면 글·댓글 쓰기가 403 COMMUNITY_40301")
    @GetMapping("/api/community/restriction")
    public ResponseEntity<BaseResponse<RestrictionResponse>> mine(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(BaseResponse.success(200, "이용 제한 조회에 성공했습니다.",
                sanctionService.myRestriction(principal.userId())));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 이용 제한 이의 신청 (U-17) ]",
            description = "**Parameters** content(1~1000자) **Returns** 접수된 이의. 지금 제한이 없으면 409 COMMUNITY_40904, 이미 했으면 409 COMMUNITY_40905")
    @PostMapping("/api/community/restriction/appeal")
    public ResponseEntity<BaseResponse<AppealResponse>> appeal(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody AppealRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(201, "이의 신청을 접수했습니다.",
                sanctionService.appeal(principal.userId(), request.content())));
    }

    @Operation(summary = "[ 운영자 | 토큰 O(ADMIN) | 이의 신청 목록 ]", description = "**Returns** 최신 먼저. 받아들이면 「이용 제한 풀기」로 푼다")
    @GetMapping("/api/admin/sanction-appeals")
    public ResponseEntity<BaseResponse<List<AppealResponse>>> appeals() {
        return ResponseEntity.ok(BaseResponse.success(200, "이의 신청 목록 조회에 성공했습니다.", sanctionService.appeals()));
    }

    @Operation(summary = "[ 운영자 | 토큰 O(ADMIN) | 이용 제한 걸기 ]",
            description = "**Parameters** userId · reason · days(1~365, 없으면 풀 때까지). ADMIN이 아니면 403")
    @PostMapping("/api/admin/sanctions")
    public ResponseEntity<BaseResponse<SanctionResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody SanctionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(201, "이용 제한을 걸었습니다.",
                sanctionService.create(principal.userId(), request)));
    }

    @Operation(summary = "[ 운영자 | 토큰 O(ADMIN) | 이용 제한 풀기 ]")
    @PostMapping("/api/admin/sanctions/{sanctionId}/lift")
    public ResponseEntity<BaseResponse<SanctionResponse>> lift(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long sanctionId) {
        return ResponseEntity.ok(BaseResponse.success(200, "이용 제한을 풀었습니다.",
                sanctionService.lift(principal.userId(), sanctionId)));
    }
}
