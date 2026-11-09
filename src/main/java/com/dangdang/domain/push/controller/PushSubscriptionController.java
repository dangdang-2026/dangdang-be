package com.dangdang.domain.push.controller;

import com.dangdang.domain.push.dto.request.PushSubscriptionRequest;
import com.dangdang.domain.push.dto.response.PushDeliveryResponse;
import com.dangdang.domain.push.dto.response.PushSubscriptionResponse;
import com.dangdang.domain.push.service.PushSendService;
import com.dangdang.domain.push.service.PushSubscriptionService;
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

@Tag(name = "Push", description = "웹 푸시 구독·테스트 알림 API")
@RestController
@RequestMapping("/api/push-subscriptions")
@RequiredArgsConstructor
public class PushSubscriptionController {

    private final PushSubscriptionService pushSubscriptionService;
    private final PushSendService pushSendService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 푸시 구독 저장 ]",
            description =
                    """
                    **Parameters** endpoint, keys.p256dh, keys.auth (브라우저 pushManager.subscribe 결과), userAgent(선택)
                    **Returns** subscriptionId. 같은 endpoint면 새 줄 없이 그 줄을 다시 살린다
                    """)
    @PostMapping
    public ResponseEntity<BaseResponse<PushSubscriptionResponse>> subscribe(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody PushSubscriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(201, "알림 구독을 저장했습니다.",
                        pushSubscriptionService.subscribe(principal.userId(), request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 푸시 구독 끊기 ]",
            description =
                    """
                    **Parameters** id (구독 번호)
                    **Returns** 없음. 내 구독이 아니면 PUSH_40401
                    """)
    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> unsubscribe(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        pushSubscriptionService.unsubscribe(principal.userId(), id);
        return ResponseEntity.ok(BaseResponse.success(200, "알림 구독을 끊었습니다.", null));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 테스트 알림 ]",
            description =
                    """
                    **Parameters** 없음
                    **Returns** 살아 있는 내 기기 전부에 보낸 결과 개수. 기기가 없으면 PUSH_40402
                    """)
    @PostMapping("/test")
    public ResponseEntity<BaseResponse<PushDeliveryResponse>> test(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(
                BaseResponse.success(200, "테스트 알림을 보냈습니다.", pushSendService.sendTest(principal.userId())));
    }
}
