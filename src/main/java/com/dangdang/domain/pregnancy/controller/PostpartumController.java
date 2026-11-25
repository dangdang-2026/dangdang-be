package com.dangdang.domain.pregnancy.controller;

import com.dangdang.domain.pregnancy.dto.request.DeliveryDateRequest;
import com.dangdang.domain.pregnancy.dto.request.HospitalizationRequest;
import com.dangdang.domain.pregnancy.dto.request.MeasurementPauseRequest;
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

@Tag(name = "Postpartum", description = "입원·다른 상황·출산·산후·졸업 API")
@RestController
@RequiredArgsConstructor
public class PostpartumController {

    private final PostpartumService postpartumService;

    @Operation(summary = "[ 사용자 | 토큰 O | 입원했어요 · 다시 켜기 ]",
            description = """
                    **Parameters** hospitalized(true 입원 · false 퇴원)
                    **Returns** state, deliveryDate, pausedReminders(취소한 예정 측정 알림 수)
                    입원하면 측정 알림이 멈춘다(기록은 그대로). 다른 상황(PAUSED)·산후에는 409 PREGNANCY_40901 · 졸업 뒤 409 PREGNANCY_40902
                    """)
    @PatchMapping("/api/me/hospitalization")
    public ResponseEntity<BaseResponse<PregnancyStateResponse>> hospitalization(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody HospitalizationRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "입원 상태를 저장했습니다.",
                postpartumService.hospitalization(principal.userId(), request.hospitalized())));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 다른 상황이에요 · 다시 켜기 ]",
            description = """
                    **Parameters** paused(true 알림 멈춤 · false 다시 켜기)
                    **Returns** state(PAUSED · PREGNANT), deliveryDate, pausedReminders(취소한 예정 측정 알림 수, 다시 켜기는 0)
                    무슨 상황인지는 묻지 않고 측정 알림만 멈춘다(기록은 그대로). 임신 중·입원 중에 멈출 수 있고, 두 번 눌러도 PAUSED.
                    임신 정보가 없으면 새로 만들고 멈춘다. 다시 켜면 PREGNANT (측정 알림은 다음 예약부터, 취소된 알림은 안 살아남)
                    산후에 멈추기 · 멈춘 상태가 아닌데 다시 켜기 409 PREGNANCY_40901 · 졸업 뒤 409 PREGNANCY_40902 · 건강정보 동의 없음 403
                    """)
    @PatchMapping("/api/me/measurement-pause")
    public ResponseEntity<BaseResponse<PregnancyStateResponse>> measurementPause(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody MeasurementPauseRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "알림 멈춤 상태를 저장했습니다.",
                postpartumService.measurementPause(principal.userId(), request.paused())));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 출산일 → 산후 모드 ]",
            description = """
                    **Parameters** deliveryDate(오늘까지)
                    **Returns** state=POSTPARTUM, deliveryDate, pausedReminders
                    측정 알림이 멈춘다. 입원 중·다른 상황(PAUSED)에서도 된다. 오늘 뒤 날짜 400 PREGNANCY_40001 · 진단일보다 앞 400 PREGNANCY_40005(같은 날은 됨) · 이미 졸업 409 PREGNANCY_40902
                    """)
    @PatchMapping("/api/me/delivery-date")
    public ResponseEntity<BaseResponse<PregnancyStateResponse>> deliver(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody DeliveryDateRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "출산일 저장에 성공했습니다.",
                postpartumService.deliver(principal.userId(), request.deliveryDate())));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 출산 되돌리기 (잘못 눌렀을 때) ]",
            description = """
                    **Returns** state=PREGNANT, deliveryDate=null, pausedReminders=0
                    산후인 동안 언제든 임신 중으로 되돌린다. 출산일·다시 물어볼 날(askOn)을 지운다.
                    측정 알림은 다음 예약부터 다시 간다(공복·자기 전은 다시 만들어지고 식후는 다음 식사부터, 그사이 취소된 알림은 안 살아남).
                    등록한 산후 검사 일정은 지우지 않지만 산후가 아닌 동안 검사 알림은 안 간다. 산후가 아니면 409 PREGNANCY_40901
                    """)
    @DeleteMapping("/api/me/delivery-date")
    public ResponseEntity<BaseResponse<PregnancyStateResponse>> undoDelivery(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(BaseResponse.success(200, "출산 기록을 되돌렸습니다.",
                postpartumService.undoDelivery(principal.userId())));
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
            description = "**Returns** keptMeasurements(기록 수 — 지우지 않은 혈당), weeksTogether(가입한 날부터 주, 최소 1), daysTogether(가입한 한국 날짜 = 1일째). 평가·연속 축하 없음. 산후가 아니면 409 PREGNANCY_40901")
    @PostMapping("/api/me/graduation")
    public ResponseEntity<BaseResponse<GraduationResponse>> graduate(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(BaseResponse.success(200, "임신 중 관리를 마쳤어요.",
                postpartumService.graduate(principal.userId())));
    }
}
