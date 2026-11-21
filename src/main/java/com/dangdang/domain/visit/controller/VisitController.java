package com.dangdang.domain.visit.controller;

import com.dangdang.domain.visit.dto.request.VisitCreateRequest;
import com.dangdang.domain.visit.dto.request.VisitUpdateRequest;
import com.dangdang.domain.visit.dto.response.VisitDetailResponse;
import com.dangdang.domain.visit.dto.response.VisitPageResponse;
import com.dangdang.domain.visit.dto.response.VisitResponse;
import com.dangdang.domain.visit.service.VisitService;
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

@Tag(name = "Visit", description = "진료 일정 API")
@RestController
@RequestMapping("/api/visits")
@RequiredArgsConstructor
public class VisitController {

    private final VisitService visitService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 진료 일정 등록 ]",
            description =
                    """
                    **Parameters** careSiteId(선택, 내 병원 id), visitAt(선택, 진료 날짜·시각, 시간대 포함. 모르면 안 보냄, 지난 날짜도 됨)
                    **Returns** 저장된 일정 {visitId, careSiteId, visitAt, createdAt, updatedAt}.
                    없거나 삭제됐거나 남의 병원이면 CARE_SITE_40401
                    """)
    @PostMapping
    public ResponseEntity<BaseResponse<VisitResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody VisitCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(201, "진료 일정을 등록했습니다.",
                        visitService.create(principal.userId(), request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 진료 일정 목록 ]",
            description =
                    """
                    **Parameters** page(기본 0, 0~10000), size(기본 20, 1~100)
                    **Returns** items[]{visitId, careSiteId, visitAt, createdAt, updatedAt}·page·size·hasNext.
                    진료 날짜 이른 순, 날짜 미정(visitAt null)은 맨 뒤. 없으면 items=[]. page·size가 범위 밖이면 GLOBAL_40001
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<VisitPageResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page는 0~10000이에요.")
            @Max(value = 10000, message = "page는 0~10000이에요.") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1~100이에요.")
            @Max(value = 100, message = "size는 1~100이에요.") int size) {
        return ResponseEntity.ok(BaseResponse.success(200, "진료 일정 목록 조회에 성공했습니다.",
                visitService.list(principal.userId(), page, size)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 진료 일정 상세 ]",
            description =
                    """
                    **Parameters** visitId
                    **Returns** 일정 + questions[] + notes[](메모마다 aiExtract 포함). 없거나 삭제됐거나 남의 일정이면 VISIT_40401
                    """)
    @GetMapping("/{visitId}")
    public ResponseEntity<BaseResponse<VisitDetailResponse>> get(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long visitId) {
        return ResponseEntity.ok(BaseResponse.success(200, "진료 일정 상세 조회에 성공했습니다.",
                visitService.get(principal.userId(), visitId)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 진료 일정 수정 ]",
            description =
                    """
                    **Parameters** visitId + 바꿀 칸만: careSiteId(null을 보내면 병원 지우기), visitAt(null을 보내면 날짜 미정)
                    **Returns** 수정된 일정. 바꿀 값이 없으면 VISIT_40001, 없거나 삭제됐거나 남의 일정이면 VISIT_40401,
                    병원이 없거나 삭제됐거나 남의 병원이면 CARE_SITE_40401
                    """)
    @PatchMapping("/{visitId}")
    public ResponseEntity<BaseResponse<VisitResponse>> update(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long visitId,
            @Valid @RequestBody VisitUpdateRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "진료 일정을 수정했습니다.",
                visitService.update(principal.userId(), visitId, request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 진료 일정 삭제 ]",
            description =
                    """
                    **Parameters** visitId
                    **Returns** 성공 시 data 없음. 이후 목록·상세에서 안 보이고, 그 일정의 질문·메모도 같이 안 보임.
                    없거나 이미 삭제됐거나 남의 일정이면 VISIT_40401
                    """)
    @DeleteMapping("/{visitId}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long visitId) {
        visitService.delete(principal.userId(), visitId);
        return ResponseEntity.ok(BaseResponse.success(200, "진료 일정을 삭제했습니다.", null));
    }
}
