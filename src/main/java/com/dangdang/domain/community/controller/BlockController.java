package com.dangdang.domain.community.controller;

import com.dangdang.domain.community.dto.request.BlockRequest;
import com.dangdang.domain.community.dto.response.BlockListResponse;
import com.dangdang.domain.community.dto.response.BlockResponse;
import com.dangdang.domain.community.service.BlockService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Block", description = "이 사람 글 안 보기 API")
@RestController
@RequestMapping("/api/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 프로필 차단 ]",
            description =
                    """
                    **Parameters** blockedProfileId
                    **Returns** blocked=true. 다시 해도 200. 자기 자신 COMMUNITY_40003, 없는 프로필 COMMUNITY_40402
                    """)
    @PostMapping
    public ResponseEntity<BaseResponse<BlockResponse>> block(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody BlockRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "프로필 차단에 성공했습니다.",
                blockService.block(principal.userId(), request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 차단 목록 ]",
            description =
                    """
                    **Parameters** cursor, size(1~100, 기본 20)
                    **Returns** items(profileId, nickname, blockedAt), nextCursor, hasNext. 최근 차단 순
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<BlockListResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1~100이에요.")
            @Max(value = 100, message = "size는 1~100이에요.") int size) {
        return ResponseEntity.ok(BaseResponse.success(200, "차단 목록 조회에 성공했습니다.",
                blockService.list(principal.userId(), cursor, size)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 프로필 차단 해제 ]",
            description =
                    """
                    **Parameters** blockedProfileId
                    **Returns** blocked=false. 차단한 적 없어도 200
                    """)
    @DeleteMapping("/{blockedProfileId}")
    public ResponseEntity<BaseResponse<BlockResponse>> unblock(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive(message = "프로필 번호를 확인해 주세요.") Long blockedProfileId) {
        return ResponseEntity.ok(BaseResponse.success(200, "프로필 차단 해제에 성공했습니다.",
                blockService.unblock(principal.userId(), blockedProfileId)));
    }
}
