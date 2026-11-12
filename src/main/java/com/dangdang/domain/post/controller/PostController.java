package com.dangdang.domain.post.controller;

import com.dangdang.domain.post.dto.request.PostCreateRequest;
import com.dangdang.domain.post.dto.request.PostUpdateRequest;
import com.dangdang.domain.post.dto.response.PostDetailResponse;
import com.dangdang.domain.post.dto.response.PostListResponse;
import com.dangdang.domain.post.enums.PostCategory;
import com.dangdang.domain.post.service.PostService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Post", description = "커뮤니티 글 API")
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 글 목록 ]",
            description =
                    """
                    **Parameters** category, q, cursor, size(1~100, 기본 20). sort는 LATEST만, audience는 ALL만
                    **Returns** items, nextCursor, hasNext. POPULAR·period·PREGNANT·POSTPARTUM은 아직 POST_40002
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<PostListResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) PostCategory category,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String audience,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1~100이에요.")
            @Max(value = 100, message = "size는 1~100이에요.") int size) {
        return ResponseEntity.ok(BaseResponse.success(200, "게시글 목록 조회에 성공했습니다.",
                postService.list(principal.userId(), category, sort, period, audience, q, cursor, size)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 글 작성 ]",
            description =
                    """
                    **Parameters** category, title(100자), content(5000자), isAnonymous(선택), poll(선택, 2~5개)
                    **Returns** 저장된 글. 프로필 없음 COMMUNITY_40401, NEWS는 POST_40301
                    """)
    @PostMapping
    public ResponseEntity<BaseResponse<PostDetailResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody PostCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(201, "게시글 작성에 성공했습니다.",
                postService.create(principal, request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 글 상세 ]",
            description =
                    """
                    **Parameters** postId
                    **Returns** 글 전체. 가려진 글은 visibility=HIDDEN에 hiddenReason만, 없거나 지운 글은 POST_40401
                    """)
    @GetMapping("/{postId}")
    public ResponseEntity<BaseResponse<PostDetailResponse>> detail(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive(message = "글 번호를 확인해 주세요.") Long postId) {
        return ResponseEntity.ok(BaseResponse.success(200, "게시글 상세 조회에 성공했습니다.",
                postService.detail(principal.userId(), postId)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 글 수정 ]",
            description =
                    """
                    **Parameters** postId, category·title·content 중 하나 이상. 투표는 못 고침
                    **Returns** 바뀐 글. 남의 글·가려진 글 POST_40401
                    """)
    @PatchMapping("/{postId}")
    public ResponseEntity<BaseResponse<PostDetailResponse>> update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive(message = "글 번호를 확인해 주세요.") Long postId,
            @Valid @RequestBody PostUpdateRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "게시글 수정에 성공했습니다.",
                postService.update(principal, postId, request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 글 삭제 ]",
            description =
                    """
                    **Parameters** postId
                    **Returns** data 없음. 지운 시각만 남기는 soft delete. 남의 글 POST_40401
                    """)
    @DeleteMapping("/{postId}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive(message = "글 번호를 확인해 주세요.") Long postId) {
        postService.delete(principal.userId(), postId);
        return ResponseEntity.ok(BaseResponse.success(200, "게시글 삭제에 성공했습니다.", null));
    }
}
