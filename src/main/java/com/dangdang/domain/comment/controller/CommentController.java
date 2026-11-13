package com.dangdang.domain.comment.controller;

import com.dangdang.domain.comment.dto.request.CommentCreateRequest;
import com.dangdang.domain.comment.dto.request.CommentUpdateRequest;
import com.dangdang.domain.comment.dto.response.CommentListResponse;
import com.dangdang.domain.comment.dto.response.CommentResponse;
import com.dangdang.domain.comment.service.CommentService;
import com.dangdang.domain.post.dto.response.ReactionResponse;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Comment", description = "커뮤니티 댓글·댓글 공감 API")
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 댓글 목록 ]",
            description =
                    """
                    **Parameters** postId
                    **Returns** items(오래된 순, parentId로 답글 구분), totalCount. 지운·가린 댓글과 그 답글은 빠진다
                    """)
    @GetMapping("/api/posts/{postId}/comments")
    public ResponseEntity<BaseResponse<CommentListResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive(message = "글 번호를 확인해 주세요.") Long postId) {
        return ResponseEntity.ok(BaseResponse.success(200, "댓글 목록 조회에 성공했습니다.",
                commentService.list(principal.userId(), postId)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 댓글 작성 ]",
            description =
                    """
                    **Parameters** postId, parentId(선택, 답글일 때), content(1000자), isAnonymous(선택)
                    **Returns** 저장된 댓글. 답글의 답글 COMMENT_40001, 글·부모 댓글 없음 POST_40401·COMMENT_40401
                    """)
    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<BaseResponse<CommentResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive(message = "글 번호를 확인해 주세요.") Long postId,
            @Valid @RequestBody CommentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(201, "댓글 작성에 성공했습니다.",
                commentService.create(principal.userId(), postId, request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 댓글 수정 ]",
            description =
                    """
                    **Parameters** commentId, content
                    **Returns** 바뀐 댓글. 남의 댓글·가려진 댓글 COMMENT_40401
                    """)
    @PatchMapping("/api/comments/{commentId}")
    public ResponseEntity<BaseResponse<CommentResponse>> update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive(message = "댓글 번호를 확인해 주세요.") Long commentId,
            @Valid @RequestBody CommentUpdateRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "댓글 수정에 성공했습니다.",
                commentService.update(principal.userId(), commentId, request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 댓글 삭제 ]",
            description =
                    """
                    **Parameters** commentId
                    **Returns** data 없음. soft delete. 남의 댓글 COMMENT_40401
                    """)
    @DeleteMapping("/api/comments/{commentId}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive(message = "댓글 번호를 확인해 주세요.") Long commentId) {
        commentService.delete(principal.userId(), commentId);
        return ResponseEntity.ok(BaseResponse.success(200, "댓글 삭제에 성공했습니다.", null));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 댓글 공감 ]",
            description =
                    """
                    **Parameters** commentId
                    **Returns** reacted=true, reactionCount. 다시 눌러도 200
                    """)
    @PostMapping("/api/comments/{commentId}/reactions")
    public ResponseEntity<BaseResponse<ReactionResponse>> addReaction(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive(message = "댓글 번호를 확인해 주세요.") Long commentId) {
        return ResponseEntity.ok(BaseResponse.success(200, "댓글 공감에 성공했습니다.",
                commentService.addReaction(principal.userId(), commentId)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 댓글 공감 취소 ]",
            description =
                    """
                    **Parameters** commentId
                    **Returns** reacted=false, reactionCount. 공감한 적 없어도 200
                    """)
    @DeleteMapping("/api/comments/{commentId}/reactions/me")
    public ResponseEntity<BaseResponse<ReactionResponse>> removeReaction(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive(message = "댓글 번호를 확인해 주세요.") Long commentId) {
        return ResponseEntity.ok(BaseResponse.success(200, "댓글 공감 취소에 성공했습니다.",
                commentService.removeReaction(principal.userId(), commentId)));
    }
}
