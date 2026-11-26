package com.dangdang.domain.admin.controller;

import com.dangdang.domain.admin.dto.response.AdminAnnouncementListResponse;
import com.dangdang.domain.admin.enums.AnnouncementState;
import com.dangdang.domain.admin.service.AdminAnnouncementQueryService;
import com.dangdang.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 쓰기·내리기는 AnnouncementController 에 있다. 여기는 운영자 목록만
@Tag(name = "Admin · Announcement", description = "운영자 당당 소식 목록 API")
@RestController
@RequiredArgsConstructor
public class AdminAnnouncementController {

    private final AdminAnnouncementQueryService adminAnnouncementQueryService;

    @Operation(
            summary = "[ 운영자 | 토큰 O(ADMIN) | 당당 소식 목록 ]",
            description =
                    """
                    **Parameters** state(없으면 전체 · LIVE · SCHEDULED · ENDED — ENDED는 예약 취소(CANCELED)도 같이)
                    **Returns** 탭 숫자(liveCount · scheduledCount · endedCount), 자리 현황(slots: 올라가 있는 개수 · 최대 칸 · full), items(올라가 있음 → 예약 → 끝남 순). 시작 전에 내린 소식은 CANCELED. ADMIN이 아니면 403
                    """)
    @GetMapping("/api/admin/announcements")
    public ResponseEntity<BaseResponse<AdminAnnouncementListResponse>> list(
            @RequestParam(required = false) AnnouncementState state) {
        return ResponseEntity.ok(BaseResponse.success(200, "당당 소식 목록 조회에 성공했습니다.",
                adminAnnouncementQueryService.list(state)));
    }
}
