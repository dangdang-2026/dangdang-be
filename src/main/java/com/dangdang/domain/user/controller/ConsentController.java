package com.dangdang.domain.user.controller;

import com.dangdang.domain.user.dto.request.ConsentRequest;
import com.dangdang.domain.user.dto.response.ConsentResponse;
import com.dangdang.domain.user.service.ConsentService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Consent", description = "약관·민감정보·선택 동의 API")
@RestController
@RequestMapping("/api/me/consents")
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentService consentService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 동의 저장 ]",
            description =
                    """
                    **Parameters** items: [{type, granted}] — 바꿀 항목만 보내도 된다 (A-01은 5개, 사진 AI 동의는 AI_PHOTO 하나)
                    **Returns** completed(필수 4개 동의 여부), 항목별 최신 상태. 필수 항목을 false로 보내면 USER_40001
                    """)
    @PostMapping
    public ResponseEntity<BaseResponse<ConsentResponse>> save(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody ConsentRequest request) {
        return ResponseEntity.ok(
                BaseResponse.success(200, "동의 저장에 성공했습니다.", consentService.save(principal.userId(), request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 동의 상태 ]",
            description =
                    """
                    **Parameters** 없음
                    **Returns** completed, 8개 항목의 최신 상태 (한 번도 안 고른 항목은 granted=false, version·occurredAt=null)
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<ConsentResponse>> status(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(
                BaseResponse.success(200, "동의 상태 조회에 성공했습니다.", consentService.getStatus(principal.userId())));
    }
}
