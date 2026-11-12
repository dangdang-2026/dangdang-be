package com.dangdang.domain.user.controller;

import com.dangdang.domain.user.dto.request.NameUpdateRequest;
import com.dangdang.domain.user.dto.response.NameUpdateResponse;
import com.dangdang.domain.user.service.UserNameService;
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
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class UserNameController {

    private final UserNameService userNameService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 내 이름 바꾸기 ]",
            description =
                    """
                    **Parameters** name(앞뒤 공백을 지운 뒤 1~10자), version(GET /api/me 의 version, BIGINT 문자열)
                    **Returns** name, version(바뀐 뒤). 입력이 틀리면 GLOBAL_40001, version이 다르면 USER_40901
                    """)
    @PatchMapping
    public ResponseEntity<BaseResponse<NameUpdateResponse>> change(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody NameUpdateRequest request) {
        return ResponseEntity.ok(
                BaseResponse.success(200, "내 이름 변경에 성공했습니다.", userNameService.change(principal.userId(), request)));
    }
}
