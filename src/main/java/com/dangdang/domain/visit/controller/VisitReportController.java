package com.dangdang.domain.visit.controller;

import com.dangdang.domain.visit.dto.request.VisitReportCreateRequest;
import com.dangdang.domain.visit.dto.response.VisitReportPageResponse;
import com.dangdang.domain.visit.dto.response.VisitReportResponse;
import com.dangdang.domain.visit.dto.response.VisitReportSaveResponse;
import com.dangdang.domain.visit.service.VisitReportService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Visit Report", description = "진료 리포트 API")
@RestController
@RequiredArgsConstructor
public class VisitReportController {

    private final VisitReportService visitReportService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 지난 리포트 목록 ]",
            description =
                    """
                    **Parameters** careSiteId(선택, 그 병원 리포트만), page(기본 0, 0~10000), size(기본 20, 1~100)
                    **Returns** items[]{id, kind, from, to, visitId, careSiteId, careSiteName, visitDate,
                    summary{measured, inTargetRate, outOfTarget, compared}, createdAt}·page·size·hasNext.
                    최근 저장 순. summary는 조회할 때 다시 계산 (기준이 없던 기간이면 compared=false, inTargetRate=null).
                    지운 병원이면 careSiteName=null, 진료용인데 그 진료 일정을 지웠으면 목록에서 빠짐.
                    없으면 items=[]. page·size가 범위 밖이면 GLOBAL_40001
                    """)
    @GetMapping("/api/visit-reports")
    public ResponseEntity<BaseResponse<VisitReportPageResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) Long careSiteId,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page는 0~10000이에요.")
            @Max(value = 10000, message = "page는 0~10000이에요.") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1~100이에요.")
            @Max(value = 100, message = "size는 1~100이에요.") int size) {
        return ResponseEntity.ok(BaseResponse.success(200, "지난 리포트 목록 조회에 성공했습니다.",
                visitReportService.list(principal.userId(), careSiteId, page, size)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 진료 리포트 조회 ]",
            description =
                    """
                    **Parameters** visitId
                    **Returns** 리포트 한 장 (볼 때마다 다시 계산, 해석·평균·그래프 없음).
                    기간: 바로 앞 진료일 ~ 이번 진료 전날 (앞 진료가 없으면 첫 혈당 기록일부터, 기록도 없으면 전날 하루, 최대 300일).
                    kind·from·to·visitId·visitDate, careSite{careSiteId, name}(없거나 지운 병원이면 null), displayName,
                    pregnancyWeeks{from, to}(예정일이 없으면 null), criteria[](기간 안 적용된 병원 기준, 오래된 순),
                    summary{measured 측정, missed 빈칸, enteredLate 나중 입력, below70, outOfTarget, inTargetRate, compared},
                    slots[]{slot, measured, outOfTarget}(하루 순서, 잴 때 없는 기록은 slot null로 맨 뒤),
                    readings[]{readingId, measuredAt, readingType, slot, value, aboveTarget, enteredLate}(원값 표),
                    questionCount(물어볼 것 수). 목표 밖은 기록마다 그때 기준으로 셈.
                    없거나 삭제됐거나 남의 일정이면 VISIT_40401, 진료 날짜가 미정이면 VISIT_40003
                    """)
    @GetMapping("/api/visits/{visitId}/report")
    public ResponseEntity<BaseResponse<VisitReportResponse>> visitReport(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long visitId) {
        return ResponseEntity.ok(BaseResponse.success(200, "진료 리포트 조회에 성공했습니다.",
                visitReportService.visitReport(principal.userId(), visitId)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 저장한 리포트 조회 ]",
            description =
                    """
                    **Parameters** reportId (리포트 저장 API에서 받은 id)
                    **Returns** 진료 리포트 조회와 같은 모양. 저장한 kind·기간·병원 그대로, 숫자는 지금 기록으로 다시 계산.
                    기간 직접(CUSTOM)이면 visitId·visitDate·questionCount가 null.
                    없거나 남의 리포트, 진료용인데 그 진료 일정을 지웠으면 VISIT_40404
                    """)
    @GetMapping("/api/visit-reports/{reportId}")
    public ResponseEntity<BaseResponse<VisitReportResponse>> savedReport(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long reportId) {
        return ResponseEntity.ok(BaseResponse.success(200, "리포트 조회에 성공했습니다.",
                visitReportService.savedReport(principal.userId(), reportId)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 리포트 저장 (진료용·기간 직접) ]",
            description =
                    """
                    **Parameters** kind(VISIT 진료용 · CUSTOM 기간 직접), from·to(yyyy-MM-dd, 둘 다 포함),
                    visitId(VISIT이면 꼭, CUSTOM이면 안 보냄), careSiteId(CUSTOM만, 선택, 보여 줄 내 병원. VISIT은 보내도 안 쓰고 그 진료 일정의 병원으로 저장)
                    **Returns** {id}. 새로 저장하면 201, 같은 kind·from·to·visitId·병원이 이미 있으면 (CUSTOM은 같은 기간이라도 병원이 다르면 따로 저장) 새로 만들지 않고 기존 id를 200.
                    리포트 숫자·표는 저장하지 않음 (볼 때마다 다시 계산). 이름 가리기는 공유 때만 쓰는 값이라 저장하지 않음.
                    from > to·300일 넘음·2000~2100년 밖·kind와 visitId가 안 맞으면 GLOBAL_40001,
                    진료 일정이 없거나 삭제됐거나 남의 것이면 VISIT_40401, CUSTOM의 병원이 그렇면 CARE_SITE_40401
                    """)
    @PostMapping("/api/visit-reports")
    public ResponseEntity<BaseResponse<VisitReportSaveResponse>> save(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody VisitReportCreateRequest request) {
        VisitReportSaveResponse saved = visitReportService.save(principal.userId(), request);
        HttpStatus status = saved.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(BaseResponse.success(status.value(), "리포트 저장에 성공했습니다.", saved));
    }
}
