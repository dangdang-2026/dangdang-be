package com.dangdang.domain.admin.controller;

import com.dangdang.domain.admin.dto.response.AdminLogPageResponse;
import com.dangdang.domain.admin.enums.AdminLogCategory;
import com.dangdang.domain.admin.service.AdminLogQueryService;
import com.dangdang.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin · Log", description = "운영자 처리 기록 API")
// 파라미터 검사(@Min·@Max·@Size)는 스프링 기본 검사 → HandlerMethodValidationException → 400 (GlobalExceptionHandler)
@RestController
@RequiredArgsConstructor
public class AdminLogController {

    private final AdminLogQueryService adminLogQueryService;

    @Operation(
            summary = "[ 운영자 | 토큰 O(ADMIN) | 처리 기록 ]",
            description =
                    """
                    **Parameters** category(없으면 전체 · REPORT · SANCTION · ANNOUNCEMENT · CARD), q(닉네임·제목 일부, 50자), page(0부터), size(1~100, 기본 30)
                    **Returns** 최근 것부터. 신고 처리(가리기·되돌리기·문제없음)와 제한·소식·카드 일을 합쳐서 보여 준다. 다음 쪽이 있으면 hasNext=true. ADMIN이 아니면 403
                    """)
    @GetMapping("/api/admin/logs")
    public ResponseEntity<BaseResponse<AdminLogPageResponse>> page(
            @RequestParam(required = false) AdminLogCategory category,
            @RequestParam(required = false) @Size(max = 50) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(BaseResponse.success(200, "처리 기록 조회에 성공했습니다.",
                adminLogQueryService.page(category, q, page, size)));
    }
}
