package com.dangdang.domain.post.controller;

import com.dangdang.domain.post.dto.response.ReactionResponse;
import com.dangdang.domain.post.service.PostReactionService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Post Reaction", description = "글 공감 API")
@RestController
@RequestMapping("/api/posts/{postId}/reactions")
@RequiredArgsConstructor
public class PostReactionController {

    private final PostReactionService reactionService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 글 공감 ]",
            description =
                    """
                    **Parameters** postId
                    **Returns** reacted=true, reactionCount. 다시 눌러도 200
                    """)
    @PostMapping
    public ResponseEntity<BaseResponse<ReactionResponse>> add(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive(message = "글 번호를 확인해 주세요.") Long postId) {
        return ResponseEntity.ok(BaseResponse.success(200, "게시글 공감에 성공했습니다.",
                reactionService.add(principal.userId(), postId)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 글 공감 취소 ]",
            description =
                    """
                    **Parameters** postId
                    **Returns** reacted=false, reactionCount. 공감한 적 없어도 200
                    """)
    @DeleteMapping("/me")
    public ResponseEntity<BaseResponse<ReactionResponse>> remove(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive(message = "글 번호를 확인해 주세요.") Long postId) {
        return ResponseEntity.ok(BaseResponse.success(200, "게시글 공감 취소에 성공했습니다.",
                reactionService.remove(principal.userId(), postId)));
    }
}
