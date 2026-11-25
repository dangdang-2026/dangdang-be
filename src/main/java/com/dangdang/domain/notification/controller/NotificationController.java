package com.dangdang.domain.notification.controller;

import com.dangdang.domain.notification.dto.response.NotificationPageResponse;
import com.dangdang.domain.notification.dto.response.NotificationReadResponse;
import com.dangdang.domain.notification.service.NotificationService;
import com.dangdang.domain.notification.stream.NotificationStreamRegistry;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.common.QueryParams;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Notification", description = "알림함 API")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationStreamRegistry notificationStreamRegistry;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 앱 안 알림 실시간 받기 (SSE) ]",
            description =
                    """
                    앱이 열려 있는 동안 연결해 두면, 알림함에 새 알림이 생기는 순간 바로 받는다 (화면 위 배너용)
                    **사용** `new EventSource(API + "/api/notifications/stream", { withCredentials: true })`
                    **이벤트** `connected`(연결 직후 1번, data "ok") · `notification`(data = 알림함 목록의 item 한 개와 같은 모양, id = 알림 번호)
                    25초마다 주석(ping)이 온다. 30분이면 서버가 닫고 브라우저가 알아서 다시 붙는다.
                    잠금화면 숨김과 상관없이 본문을 그대로 준다 (앱 안은 로그인 상태)
                    """)
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthPrincipal principal, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");   // Nginx가 모아 두지 않고 바로 흘려보내게
        response.setHeader("Cache-Control", "no-cache");
        return notificationStreamRegistry.connect(principal.userId());
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 알림함 목록 ]",
            description =
                    """
                    **Parameters** page(기본 0), size(기본 20, 1~100)
                    **Returns** items, page, size, hasNext. 최신이 위로
                    type: MEASURE · VISIT_BEFORE·VISIT_TODAY(진료 전날·당일, 제목은 「11월 10일(화) 진료가 있어요」처럼 날짜, body 「병원 · 오전 10:00」(병원이 없거나 지웠으면 시각만), deepLink `/visits/{visitId}`) · VISIT_NOTE · POSTPARTUM(산후 검사, 제목은 「11월 10일(화) ○○ 검사가 있어요」처럼 날짜) · MARKETING(광고성 정보 동의·거부 결과, body 「2026년 11월 24일 · 보낸 곳 당당」)
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
