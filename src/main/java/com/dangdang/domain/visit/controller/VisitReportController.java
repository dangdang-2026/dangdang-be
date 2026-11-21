package com.dangdang.domain.visit.controller;

import com.dangdang.domain.visit.dto.request.VisitReportCreateRequest;
import com.dangdang.domain.visit.dto.response.VisitReportSaveResponse;
import com.dangdang.domain.visit.service.VisitReportService;
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

@Tag(name = "Visit Report", description = "진료 리포트 API")
@RestController
@RequestMapping("/api/visit-reports")
@RequiredArgsConstructor
public class VisitReportController {

    private final VisitReportService visitReportService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 리포트 저장 (진료용·기간 직접) ]",
            description =
                    """
                    **Parameters** kind(VISIT 진료용 · CUSTOM 기간 직접), from·to(yyyy-MM-dd, 둘 다 포함),
                    visitId(VISIT이면 꼭, CUSTOM이면 안 보냄), careSiteId(선택, 보여 줄 내 병원)
                    **Returns** {id}. 새로 저장하면 201, 같은 kind·from·to·visitId가 이미 있으면 새로 만들지 않고 기존 id를 200.
                    리포트 숫자·표는 저장하지 않음 (볼 때마다 다시 계산). 이름 가리기는 공유 때만 쓰는 값이라 저장하지 않음.
                    from > to·300일 넘음·2000~2100년 밖·kind와 visitId가 안 맞으면 GLOBAL_40001,
                    진료 일정이 없거나 삭제됐거나 남의 것이면 VISIT_40401, 병원이 그렇면 CARE_SITE_40401
                    """)
    @PostMapping
    public ResponseEntity<BaseResponse<VisitReportSaveResponse>> save(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody VisitReportCreateRequest request) {
        VisitReportSaveResponse saved = visitReportService.save(principal.userId(), request);
        HttpStatus status = saved.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(BaseResponse.success(status.value(), "리포트 저장에 성공했습니다.", saved));
    }
}
