package com.dangdang.domain.caresite.controller;

import com.dangdang.domain.caresite.dto.request.CareSiteCreateRequest;
import com.dangdang.domain.caresite.dto.request.CareSiteUpdateRequest;
import com.dangdang.domain.caresite.dto.response.CareSiteResponse;
import com.dangdang.domain.caresite.service.CareSiteService;
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

import java.util.List;

@Tag(name = "CareSite", description = "다니는 병원 API")
@RestController
@RequestMapping("/api/care-sites")
@RequiredArgsConstructor
public class CareSiteController {

    private final CareSiteService careSiteService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 병원 등록 ]",
            description =
                    """
                    **Parameters** name(필수, 50자), department(OBGYN/ENDO/NUTRITION/ETC, 필수), hiraCode(선택, 심평원 검색으로 고른 경우), address(선택, 200자), phone(선택, 20자), roleNote(선택, 30자), primary(선택, 기본 false), sortOrder(선택, 0 이상, 기본 맨 뒤)
                    **Returns** 저장된 병원. primary=true면 원래 대표 병원은 자동으로 풀림. 입력이 잘못되면 GLOBAL_40001
                    """)
    @PostMapping
    public ResponseEntity<BaseResponse<CareSiteResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody CareSiteCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(201, "병원을 등록했습니다.",
                        careSiteService.create(principal.userId(), request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 병원 목록 ]",
            description =
                    """
                    **Parameters** 없음
                    **Returns** 내 병원 전부. 대표 병원 먼저 → sortOrder 작은 순 → 먼저 등록한 순. 없으면 []
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<List<CareSiteResponse>>> list(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(BaseResponse.success(200, "병원 목록 조회에 성공했습니다.",
                careSiteService.list(principal.userId())));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 병원 수정 ]",
            description =
                    """
                    **Parameters** careSiteId + 바꿀 칸만: name, department, hiraCode, address, phone, roleNote(비우려면 ""), primary(true=대표로 지정, false=대표 해제), sortOrder
                    **Returns** 수정된 병원. primary=true면 원래 대표 병원은 자동으로 풀림.
                    바꿀 값이 없으면 CARE_SITE_40001, 없거나 삭제됐거나 남의 병원이면 CARE_SITE_40401
                    """)
    @PatchMapping("/{careSiteId}")
    public ResponseEntity<BaseResponse<CareSiteResponse>> update(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long careSiteId,
            @Valid @RequestBody CareSiteUpdateRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "병원 정보를 수정했습니다.",
                careSiteService.update(principal.userId(), careSiteId, request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 병원 삭제 ]",
            description =
                    """
                    **Parameters** careSiteId
                    **Returns** 성공 시 data 없음. 이후 목록에서 안 보임. 대표 병원이었으면 대표도 풀림.
                    없거나 이미 삭제됐거나 남의 병원이면 CARE_SITE_40401
                    """)
    @DeleteMapping("/{careSiteId}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long careSiteId) {
        careSiteService.delete(principal.userId(), careSiteId);
        return ResponseEntity.ok(BaseResponse.success(200, "병원을 삭제했습니다.", null));
    }
}
