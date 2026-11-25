package com.dangdang.domain.post.controller;

import com.dangdang.domain.comment.dto.response.MyCommentListResponse;
import com.dangdang.domain.post.dto.response.MyCommunityCountsResponse;
import com.dangdang.domain.post.dto.response.PostListResponse;
import com.dangdang.domain.post.service.MyActivityService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "My Community", description = "마이 › 커뮤니티 활동 API")
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MyActivityController {

    private final MyActivityService myActivityService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 내가 쓴 글 ]",
            description =
                    """
                    **Parameters** cursor, size(1~100, 기본 20)
                    **Returns** items, nextCursor, hasNext. 글 목록(GET /api/posts)과 같은 모양, 최신 먼저.
                    지운 글·가린 글 제외. 프로필이 없으면 빈 목록. 다른 목록의 커서 POST_40001
                    """)
    @GetMapping("/posts")
    public ResponseEntity<BaseResponse<PostListResponse>> myPosts(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1~100이에요.")
            @Max(value = 100, message = "size는 1~100이에요.") int size) {
        return ResponseEntity.ok(BaseResponse.success(200, "내가 쓴 글 조회에 성공했습니다.",
                myActivityService.myPosts(principal.userId(), cursor, size)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 내가 쓴 댓글 ]",
            description =
                    """
                    **Parameters** cursor, size(1~100, 기본 20)
                    **Returns** items(id, postId, postTitle, parentId, content, anonymous, reactionCount, createdAt), nextCursor, hasNext. 최신 먼저.
                    지운·가린 댓글, 지운·가린 글의 댓글, 안 보는 사람 글의 댓글 제외. 프로필이 없으면 빈 목록
                    """)
    @GetMapping("/comments")
    public ResponseEntity<BaseResponse<MyCommentListResponse>> myComments(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1~100이에요.")
            @Max(value = 100, message = "size는 1~100이에요.") int size) {
        return ResponseEntity.ok(BaseResponse.success(200, "내가 쓴 댓글 조회에 성공했습니다.",
                myActivityService.myComments(principal.userId(), cursor, size)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 공감한 글 ]",
            description =
                    """
                    **Parameters** cursor, size(1~100, 기본 20)
                    **Returns** items, nextCursor, hasNext. 글 목록과 같은 모양, 공감한 때가 최신인 것 먼저.
                    지운·가린 글, 안 보는 사람의 글 제외. 프로필이 없으면 빈 목록
                    """)
    @GetMapping("/reactions")
    public ResponseEntity<BaseResponse<PostListResponse>> myReactions(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1~100이에요.")
            @Max(value = 100, message = "size는 1~100이에요.") int size) {
        return ResponseEntity.ok(BaseResponse.success(200, "공감한 글 조회에 성공했습니다.",
                myActivityService.myReactions(principal.userId(), cursor, size)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 커뮤니티 활동 개수 ]",
            description =
                    """
                    **Parameters** 없음
                    **Returns** postCount, commentCount, reactedPostCount, blockedCount (M-14 커뮤니티 설정).
                    위 세 목록 · GET /api/blocks와 같은 조건으로 센다 (blockedCount는 사람 수가 아니라 차단한 줄 수). 프로필이 없으면 전부 0
                    """)
    @GetMapping("/community-activity/counts")
    public ResponseEntity<BaseResponse<MyCommunityCountsResponse>> counts(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(BaseResponse.success(200, "커뮤니티 활동 개수 조회에 성공했습니다.",
                myActivityService.counts(principal.userId())));
    }
}
