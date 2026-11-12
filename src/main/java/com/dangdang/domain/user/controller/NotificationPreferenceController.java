package com.dangdang.domain.user.controller;

import com.dangdang.domain.user.dto.request.NotificationPreferenceUpdateRequest;
import com.dangdang.domain.user.dto.response.NotificationPreferenceResponse;
import com.dangdang.domain.user.service.NotificationPreferenceService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "NotificationPreference", description = "알림 설정 API")
@RestController
@RequestMapping("/api/users/me/notification-preferences")
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final NotificationPreferenceService notificationPreferenceService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 알림 설정 조회 ]",
            description =
                    """
                    **Parameters** 없음
                    **Returns** measurementEnabled, scheduleEnabled, hideContent, renotifyEnabled, fastingAlarmTime, bedtimeAlarmTime, updatedAt. 저장한 적 없으면 기본값
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<NotificationPreferenceResponse>> get(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(BaseResponse.success(200, "알림 설정 조회에 성공했습니다.",
                notificationPreferenceService.get(principal.userId())));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 알림 설정 변경 ]",
            description =
                    """
                    **Parameters** 조회 응답의 칸 중 바꿀 것만 (시각은 HH:mm). 빈 객체·null은 400
                    **Returns** 바뀐 뒤 설정 전체
                    """)
    @PatchMapping
    public ResponseEntity<BaseResponse<NotificationPreferenceResponse>> update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody NotificationPreferenceUpdateRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "알림 설정 변경에 성공했습니다.",
                notificationPreferenceService.update(principal.userId(), request)));
    }
}
