package com.dangdang.domain.post.controller;

import com.dangdang.domain.post.dto.request.PostPrecheckRequest;
import com.dangdang.domain.post.dto.response.PostPrecheckResponse;
import com.dangdang.domain.post.service.PostPrecheckService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Post Precheck", description = "글 올리기 전 AI 확인 API")
@RestController
@RequestMapping("/api/posts/precheck")
@RequiredArgsConstructor
public class PostPrecheckController {

    private final PostPrecheckService postPrecheckService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 글 올리기 전 확인 (AI) ]",
            description =
                    """
                    **Parameters** title(선택, 100자), content(필수, 5000자)
                    **Returns** issues[{kind, quote}], checked. kind: DOSE(약·용량 → U-15) · URGENT_MEDICAL(위급 → U-30) · MIND(마음 위기 → U-30)
                    issues가 비어 있으면 바로 POST /api/posts. AI 서버가 실패하면 issues=[], checked=false (글쓰기는 막지 않음)
                    커뮤니티 AI 확인 동의(COMMUNITY_AI_CHECK)가 없으면 GPT 없이 규칙만으로 검사 (응답 모양은 같음)
                    """)
    @PostMapping
    public ResponseEntity<BaseResponse<PostPrecheckResponse>> precheck(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody PostPrecheckRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "글 확인에 성공했습니다.",
                postPrecheckService.precheck(principal.userId(), request)));
    }
}
