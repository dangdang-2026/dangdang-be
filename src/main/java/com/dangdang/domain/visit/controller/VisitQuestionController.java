package com.dangdang.domain.visit.controller;

import com.dangdang.domain.visit.dto.request.VisitQuestionCreateRequest;
import com.dangdang.domain.visit.dto.response.VisitQuestionDeleteResponse;
import com.dangdang.domain.visit.dto.response.VisitQuestionResponse;
import com.dangdang.domain.visit.service.VisitQuestionService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Visit", description = "진료 일정 API")
@RestController
@RequestMapping("/api/visits/{visitId}/questions")
@RequiredArgsConstructor
public class VisitQuestionController {

    private final VisitQuestionService visitQuestionService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 진료 질문 목록 ]",
            description =
                    """
                    **Parameters** visitId
                    **Returns** 질문 [{questionId, content, source(USER/CHATBOT), sortOrder, createdAt}]. sortOrder 작은 순 → 먼저 만든 순, 없으면 [].
                    없거나 삭제됐거나 남의 일정이면 VISIT_40401
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<List<VisitQuestionResponse>>> list(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long visitId) {
        return ResponseEntity.ok(BaseResponse.success(200, "진료 질문 목록 조회에 성공했습니다.",
                visitQuestionService.list(principal.userId(), visitId)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 진료 질문 등록 ]",
            description =
                    """
                    **Parameters** visitId, content(필수, 앞뒤 공백 빼고 1~500자), sortOrder(선택, 0 이상, 기본 맨 뒤)
                    **Returns** 저장된 질문. source는 서버가 USER로 정함 (보내도 무시).
                    내용이 비었거나 길면 GLOBAL_40001, 없거나 삭제됐거나 남의 일정이면 VISIT_40401
                    """)
    @PostMapping
    public ResponseEntity<BaseResponse<VisitQuestionResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long visitId,
            @Valid @RequestBody VisitQuestionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(201, "진료 질문을 등록했습니다.",
                        visitQuestionService.create(principal.userId(), visitId, request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 진료 질문 삭제 ]",
            description =
                    """
                    **Parameters** visitId, questionId
                    **Returns** {questionId, deletedAt}. 이미 지운 질문을 또 지우면 처음 지운 시각으로 200.
                    없거나 삭제됐거나 남의 일정이면 VISIT_40401, 없는 질문이거나 다른 일정의 질문이면 VISIT_40402
                    """)
    @DeleteMapping("/{questionId}")
    public ResponseEntity<BaseResponse<VisitQuestionDeleteResponse>> delete(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long visitId,
            @PathVariable Long questionId) {
        return ResponseEntity.ok(BaseResponse.success(200, "진료 질문을 삭제했습니다.",
                visitQuestionService.delete(principal.userId(), visitId, questionId)));
    }
}
