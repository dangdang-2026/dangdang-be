package com.dangdang.domain.post.controller;

import com.dangdang.domain.post.dto.request.PollVoteRequest;
import com.dangdang.domain.post.dto.response.PollResponse;
import com.dangdang.domain.post.service.PollVoteService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Poll", description = "커뮤니티 투표 API")
@RestController
@RequestMapping("/api/posts/{postId}/poll/votes")
@RequiredArgsConstructor
public class PollVoteController {

    private final PollVoteService voteService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 투표 ]",
            description =
                    """
                    **Parameters** postId, optionIndex(0부터)
                    **Returns** options(voteCount 포함), totalVotes, myOptionIndex. 같은 항목 다시 200, 다른 항목 POST_40901
                    투표 없는 글 POST_40402, 없는 번호 POST_40006
                    """)
    @PostMapping
    public ResponseEntity<BaseResponse<PollResponse>> vote(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive(message = "글 번호를 확인해 주세요.") Long postId,
            @Valid @RequestBody PollVoteRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "투표에 성공했습니다.",
                voteService.vote(principal.userId(), postId, request)));
    }
}
