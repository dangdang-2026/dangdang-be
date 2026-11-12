package com.dangdang.domain.user.controller;

import com.dangdang.domain.user.dto.request.DueDateUpdateRequest;
import com.dangdang.domain.user.dto.response.DueDateResponse;
import com.dangdang.domain.user.service.DueDateService;
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
@RequestMapping("/api/me/due-date")
@RequiredArgsConstructor
public class DueDateController {

    private final DueDateService dueDateService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 출산 예정일 저장·변경 ]",
            description =
                    """
                    **Parameters** dueDate(yyyy-MM-dd, 필수)
                    **Returns** dueDate. 진행 중인 임신이 없으면 새로 만들고, 있으면 그 줄을 고친다. 형식이 틀리면 GLOBAL_40001
                    """)
    @PatchMapping
    public ResponseEntity<BaseResponse<DueDateResponse>> save(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody DueDateUpdateRequest request) {
        return ResponseEntity.ok(
                BaseResponse.success(200, "출산 예정일을 저장했습니다.", dueDateService.save(principal.userId(), request)));
    }
}
