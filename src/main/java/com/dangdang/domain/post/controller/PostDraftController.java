package com.dangdang.domain.post.controller;

import com.dangdang.domain.post.dto.request.PostDraftRequest;
import com.dangdang.domain.post.dto.response.PostDraftListResponse;
import com.dangdang.domain.post.dto.response.PostDraftResponse;
import com.dangdang.domain.post.service.PostDraftService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Post Draft", description = "커뮤니티 임시 저장 API")
@RestController
@RequestMapping("/api/posts/drafts")
@RequiredArgsConstructor
public class PostDraftController {

    private final PostDraftService draftService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 임시 저장 ]",
            description =
                    """
                    **Parameters** category, title, content, isAnonymous, poll (전부 선택)
                    **Returns** 저장된 임시 글. 한 사람에 하나라 있으면 덮어쓴다
                    """)
    @PutMapping
    public ResponseEntity<BaseResponse<PostDraftResponse>> save(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody PostDraftRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "게시글 임시저장에 성공했습니다.",
                draftService.save(principal.userId(), request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 내 임시 저장 글 ]",
            description =
                    """
                    **Parameters** 없음
                    **Returns** items(0개 또는 1개), nextCursor=null, hasNext=false
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<PostDraftListResponse>> getMine(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(BaseResponse.success(200, "내 게시글 임시저장 조회에 성공했습니다.",
                draftService.getMine(principal.userId())));
    }
}
