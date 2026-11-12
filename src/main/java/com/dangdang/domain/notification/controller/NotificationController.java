package com.dangdang.domain.notification.controller;

import com.dangdang.domain.notification.dto.response.NotificationPageResponse;
import com.dangdang.domain.notification.dto.response.NotificationReadResponse;
import com.dangdang.domain.notification.service.NotificationService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.common.QueryParams;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Notification", description = "알림함 API")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 알림함 목록 ]",
            description =
                    """
                    **Parameters** page(기본 0), size(기본 20, 1~100)
                    **Returns** items, page, size, hasNext. 최신이 위로
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<NotificationPageResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return ResponseEntity.ok(BaseResponse.success(200, "알림 목록 조회에 성공했습니다.",
                notificationService.list(principal.userId(),
                        QueryParams.intInRange(page, 0, 0, Integer.MAX_VALUE, "page"),
                        QueryParams.intInRange(size, 20, 1, 100, "size"))));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 알림 읽음 처리 ]",
            description =
                    """
                    **Parameters** id (알림 번호)
                    **Returns** id, readAt. 이미 읽었으면 그대로 200, 남의 알림 NOTIFICATION_40401
                    """)
    @PatchMapping("/{id}/read")
    public ResponseEntity<BaseResponse<NotificationReadResponse>> read(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        return ResponseEntity.ok(BaseResponse.success(200, "알림 읽음 처리에 성공했습니다.",
                notificationService.markRead(principal.userId(), id)));
    }
}
