package com.dangdang.domain.caresite.controller;

import com.dangdang.domain.caresite.dto.request.CareSiteCreateRequest;
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
}
