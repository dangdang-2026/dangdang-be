package com.dangdang.domain.user.controller;

import com.dangdang.domain.user.dto.request.CareModeUpdateRequest;
import com.dangdang.domain.user.dto.request.ContactMemoUpdateRequest;
import com.dangdang.domain.user.dto.request.DiagnosisDateUpdateRequest;
import com.dangdang.domain.user.dto.response.PregnancyProfileResponse;
import com.dangdang.domain.user.service.PregnancyProfileService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "내 정보 API")
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class PregnancyProfileController {

    private final PregnancyProfileService pregnancyProfileService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 임신성 당뇨 진단일 저장·변경 ]",
            description =
                    """
                    **Parameters** diagnosisDate(yyyy-MM-dd, 필수)
                    **Returns** diagnosisDate, careMode, contactMemo, contactMemoUpdatedAt. 진행 중인 임신이 없으면 새로 만든다.
                    형식이 틀리면 GLOBAL_40001, 오늘(한국) 뒤 날짜 PREGNANCY_40003, 출산 예정일보다 뒤 PREGNANCY_40004,
                    건강정보 동의 없음 USER_40302, 졸업한 뒤 PREGNANCY_40902
                    """)
    @PatchMapping("/diagnosis-date")
    public ResponseEntity<BaseResponse<PregnancyProfileResponse>> diagnosisDate(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody DiagnosisDateUpdateRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "진단일을 저장했습니다.",
                pregnancyProfileService.changeDiagnosisDate(principal.userId(), request.diagnosisDate())));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 관리 모드 바꾸기 ]",
            description =
                    """
                    **Parameters** careMode(DIET 식사 관리 · INSULIN 인슐린 함께 · ORAL 먹는 약 함께, 대문자 그대로, 필수)
                    **Returns** diagnosisDate, careMode, contactMemo, contactMemoUpdatedAt. 덮어쓴다 (바뀐 기록은 남기지 않음).
                    다른 값이면 GLOBAL_40001, 건강정보 동의 없음 USER_40302, 졸업한 뒤 PREGNANCY_40902
                    """)
    @PatchMapping("/care-mode")
    public ResponseEntity<BaseResponse<PregnancyProfileResponse>> careMode(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody CareModeUpdateRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "관리 모드를 저장했습니다.",
                pregnancyProfileService.changeCareMode(principal.userId(), request.careMode())));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 연락 기준 메모 저장·지우기 ]",
            description =
                    """
                    **Parameters** contactMemo(앞뒤 공백을 지운 뒤 100자까지, 한 줄. 빈 글·null·칸 없음이면 지움)
                    **Returns** diagnosisDate, careMode, contactMemo, contactMemoUpdatedAt(글이 바뀔 때만 새 시각, 지우면 null).
                    100자 넘음·줄바꿈 GLOBAL_40001, 건강정보 동의 없음 USER_40302, 졸업한 뒤 PREGNANCY_40902
                    """)
    @PatchMapping("/contact-memo")
    public ResponseEntity<BaseResponse<PregnancyProfileResponse>> contactMemo(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody ContactMemoUpdateRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "연락 기준 메모를 저장했습니다.",
                pregnancyProfileService.changeContactMemo(principal.userId(), request.contactMemo())));
    }
}
