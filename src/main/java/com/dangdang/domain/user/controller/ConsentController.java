package com.dangdang.domain.user.controller;

import com.dangdang.domain.user.dto.request.ConsentRequest;
import com.dangdang.domain.user.dto.response.ConsentResponse;
import com.dangdang.domain.user.service.ConsentService;
import com.dangdang.domain.user.service.SensitiveConsentWithdrawalService;
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
    private final SensitiveConsentWithdrawalService sensitiveConsentWithdrawalService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 동의 저장 ]",
            description =
                    """
                    **Parameters** items: [{type, granted}] — 바꿀 항목만 보내도 된다 (A-01은 5개, 사진 AI 동의는 AI_PHOTO 하나, U-04 「확인했어요」는 COMMUNITY_AI_CHECK 하나)
                    **Returns** completed(필수 4개를 지금 문서 버전으로 동의했는지), 항목별 최신 상태. 필수 항목을 false로 보내면 USER_40001
                    건강정보(SENSITIVE) 철회는 POST /api/me/consents/sensitive/withdraw
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
                    **Returns** completed, 9개 항목의 최신 상태 (한 번도 안 골랐거나 예전 문서 버전이면 granted=false. 안 고른 항목은 version·occurredAt=null)
                    needsReconsent=true면 예전 문서 버전에 동의한 것 (거절이 아니라 문서가 바뀌어서 다시 물어야 함)
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<ConsentResponse>> status(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(
                BaseResponse.success(200, "동의 상태 조회에 성공했습니다.", consentService.getStatus(principal.userId())));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 건강정보 동의 철회 (M-07b) ]",
            description =
                    """
                    **Parameters** 없음
                    **Returns** 동의 상태 (completed=false, SENSITIVE granted=false). 아래를 바로 지운다 (되돌릴 수 없음, 지운 기록 포함):
                    혈당 · 식사(음식 항목) · 병원 기준 · 측정 알림 · 건강 알림함 · 임신 정보 · 산후 검사 · 진료 일정·질문·메모 · 진료 리포트 · 다니는 병원 · 식사 안내 · 당당이 대화
                    남는 것: 계정 · 커뮤니티 글·댓글 · 내 음식 · 알림 설정(측정 알림은 끔). AI_CHAT_RECORDS 동의도 같이 끈다 (철회 뒤 다시 켰어도).
                    다음에 /api/me 의 consentCompleted=false → A-01. 두 번 불러도 200 (이력은 한 줄)
                    철회 뒤(다시 동의하기 전) 건강 기록을 저장·수정하는 API와 챗봇 질문은 403 USER_40302 (지우기·조회는 됨)
                    """)
    @PostMapping("/sensitive/withdraw")
    public ResponseEntity<BaseResponse<ConsentResponse>> withdrawSensitive(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(BaseResponse.success(200, "건강정보 동의를 철회했습니다.",
                sensitiveConsentWithdrawalService.withdraw(principal.userId())));
    }
}
