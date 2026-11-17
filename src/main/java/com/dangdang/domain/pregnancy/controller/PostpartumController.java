package com.dangdang.domain.pregnancy.controller;

import com.dangdang.domain.pregnancy.dto.request.DeliveryDateRequest;
import com.dangdang.domain.pregnancy.dto.request.HospitalizationRequest;
import com.dangdang.domain.pregnancy.dto.request.RemindLaterRequest;
import com.dangdang.domain.pregnancy.dto.response.GraduationResponse;
import com.dangdang.domain.pregnancy.dto.response.PostpartumHomeResponse;
import com.dangdang.domain.pregnancy.dto.response.PregnancyStateResponse;
import com.dangdang.domain.pregnancy.service.PostpartumService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Postpartum", description = "입원·출산·산후·졸업 API")
@RestController
@RequiredArgsConstructor
public class PostpartumController {

    private final PostpartumService postpartumService;

    @Operation(summary = "[ 사용자 | 토큰 O | 입원했어요 · 다시 켜기 ]",
            description = """
                    **Parameters** hospitalized(true 입원 · false 퇴원)
                    **Returns** state, deliveryDate, pausedReminders(취소한 예정 측정 알림 수)
                    입원하면 측정 알림이 멈춘다(기록은 그대로). 산후에는 409 PREGNANCY_40901
                    """)
    @PatchMapping("/api/me/hospitalization")
    public ResponseEntity<BaseResponse<PregnancyStateResponse>> hospitalization(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody HospitalizationRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "입원 상태를 저장했습니다.",
                postpartumService.hospitalization(principal.userId(), request.hospitalized())));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 출산일 → 산후 모드 ]",
            description = """
                    **Parameters** deliveryDate(오늘까지)
                    **Returns** state=POSTPARTUM, deliveryDate, pausedReminders
                    측정 알림이 멈춘다. 오늘 뒤 날짜 400 PREGNANCY_40001 · 이미 졸업 409 PREGNANCY_40902
                    """)
    @PatchMapping("/api/me/delivery-date")
    public ResponseEntity<BaseResponse<PregnancyStateResponse>> deliver(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody DeliveryDateRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "출산일 저장에 성공했습니다.",
                postpartumService.deliver(principal.userId(), request.deliveryDate())));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 산후 검사 날짜 나중에 다시 물어보기 ]",
            description = "**Parameters** remindOn(오늘이나 그 뒤) **Returns** remindOn. 그날부터 산후 홈 askToday=true. 지난 날 400 PREGNANCY_40002 · 산후가 아니면 409")
    @PostMapping("/api/me/remind-later")
    public ResponseEntity<BaseResponse<Map<String, Object>>> remindLater(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody RemindLaterRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "다시 물어볼 날 저장에 성공했습니다.",
                Map.of("topic", "POSTPARTUM_TEST_DATE",
                        "remindOn", postpartumService.remindLater(principal.userId(), request.remindOn()))));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 산후 홈 ]",
            description = "**Returns** deliveryDate, dayAfterDelivery(출산일=1일째), askOn, askToday, upcomingTests. 산후가 아니면 409")
    @GetMapping("/api/postpartum")
    public ResponseEntity<BaseResponse<PostpartumHomeResponse>> home(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(BaseResponse.success(200, "산후 홈 조회에 성공했습니다.",
                postpartumService.home(principal.userId())));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 임신 중 관리 졸업 ]",
            description = "**Returns** keptMeasurements(저장한 혈당 수), weeksTogether(가입한 날부터 주). 평가·연속 축하 없음. 산후가 아니면 409")
    @PostMapping("/api/me/graduation")
    public ResponseEntity<BaseResponse<GraduationResponse>> graduate(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(BaseResponse.success(200, "졸업을 축하해요.",
                postpartumService.graduate(principal.userId())));
    }
}
