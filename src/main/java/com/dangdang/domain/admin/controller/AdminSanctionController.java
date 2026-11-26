package com.dangdang.domain.admin.controller;

import com.dangdang.domain.admin.dto.response.AdminSanctionListResponse;
import com.dangdang.domain.admin.enums.SanctionListStatus;
import com.dangdang.domain.admin.service.AdminSanctionQueryService;
import com.dangdang.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 걸기·풀기·이의 신청 목록은 CommunityRestrictionController 에 있다. 여기는 운영자 목록만
@Tag(name = "Admin · Sanction", description = "운영자 이용 제한 목록 API")
@RestController
@RequiredArgsConstructor
public class AdminSanctionController {

    private final AdminSanctionQueryService adminSanctionQueryService;

    @Operation(
            summary = "[ 운영자 | 토큰 O(ADMIN) | 이용 제한 목록 ]",
            description =
                    """
                    **Parameters** status(ACTIVE 기본 · ENDED)
                    **Returns** 탭 숫자(activeCount · endedCount · openAppealCount) + items. 제한 중은 먼저 풀리는 순서(풀 때까지는 맨 뒤), 끝난 제한은 최근에 끝난 순서와 endedBy(LIFTED · EXPIRED). 닉네임은 커뮤니티 프로필에서, 없으면 displayName. ADMIN이 아니면 403
                    """)
    @GetMapping("/api/admin/sanctions")
    public ResponseEntity<BaseResponse<AdminSanctionListResponse>> list(
            @RequestParam(defaultValue = "ACTIVE") SanctionListStatus status) {
        return ResponseEntity.ok(BaseResponse.success(200, "이용 제한 목록 조회에 성공했습니다.",
                adminSanctionQueryService.list(status)));
    }
}
