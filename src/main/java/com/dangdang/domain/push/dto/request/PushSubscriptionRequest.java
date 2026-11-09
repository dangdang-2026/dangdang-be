package com.dangdang.domain.push.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 브라우저 pushManager.subscribe() 결과 모양 그대로
public record PushSubscriptionRequest(
        @NotBlank(message = "알림 주소가 비어 있어요.")
        @Size(max = 2048, message = "알림 주소가 너무 길어요.")
        @Pattern(
                // 서버가 이 주소로 직접 요청을 보내니까, 브라우저 푸시 서버 주소만 받는다
                regexp = "^https://([a-z0-9-]+\\.)*(googleapis\\.com|google\\.com|push\\.apple\\.com"
                        + "|push\\.services\\.mozilla\\.com|notify\\.windows\\.com)/.*$",
                message = "지원하지 않는 알림 주소예요.")
        String endpoint,

        @NotNull(message = "알림 키가 비어 있어요.")
        @Valid
        Keys keys,

        @Size(max = 255, message = "기기 정보가 너무 길어요.")
        String userAgent) {

    public record Keys(
            @NotBlank(message = "알림 키가 비어 있어요.")
            @Size(max = 255, message = "알림 키가 너무 길어요.")
            String p256dh,

            @NotBlank(message = "알림 키가 비어 있어요.")
            @Size(max = 255, message = "알림 키가 너무 길어요.")
            String auth) {
    }
}
