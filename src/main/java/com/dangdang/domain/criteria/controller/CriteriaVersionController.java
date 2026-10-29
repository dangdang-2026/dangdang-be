package com.dangdang.domain.criteria.controller;

import com.dangdang.domain.criteria.dto.request.CriteriaVersionCreateRequest;
import com.dangdang.domain.criteria.dto.response.CriteriaVersionResponse;
import com.dangdang.domain.criteria.service.CriteriaService;
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

@Tag(name = "Criteria", description = "병원 기준(식후 측정 시간·재는 때·목표) API")
@RestController
@RequestMapping("/api/criteria-versions")
@RequiredArgsConstructor
public class CriteriaVersionController {

    private final CriteriaService criteriaService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 병원 기준 등록 ]",
            description =
                    """
                    **Parameters** postMealMinutes(30~180, 10분 단위, null=아직 몰라요), measureSlots, fastingTarget·postMealTarget(미만, null=비교 안 함), sourceNote, effectiveFrom(비우면 오늘)
                    **Returns** 저장된 기준. 덮어쓰지 않고 새 줄로 쌓인다. 범위 밖이면 GLOBAL_40001
                    """)
    @PostMapping
    public ResponseEntity<BaseResponse<CriteriaVersionResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody CriteriaVersionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(201, "병원 기준을 저장했습니다.",
                        criteriaService.create(principal.userId(), request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 병원 기준 이력 ]",
            description =
                    """
                    **Parameters** 없음
                    **Returns** 이 사람의 기준 전부 (적용일 늦은 순 → 같으면 나중에 저장한 것 먼저)
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<List<CriteriaVersionResponse>>> history(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(
                BaseResponse.success(200, "병원 기준 이력 조회에 성공했습니다.", criteriaService.history(principal.userId())));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 지금 병원 기준 ]",
            description =
                    """
                    **Parameters** 없음
                    **Returns** 오늘 적용 중인 기준 1개. 한 번도 안 적었으면 data 없음
                    """)
    @GetMapping("/current")
    public ResponseEntity<BaseResponse<CriteriaVersionResponse>> current(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(
                BaseResponse.success(200, "지금 기준 조회에 성공했습니다.", criteriaService.current(principal.userId())));
    }
}

