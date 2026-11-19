package com.dangdang.domain.visit.controller;

import com.dangdang.domain.visit.dto.request.VisitNoteRequest;
import com.dangdang.domain.visit.dto.response.VisitNoteResponse;
import com.dangdang.domain.visit.service.VisitNoteService;
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
@RequestMapping("/api/visits/{visitId}/notes")
@RequiredArgsConstructor
public class VisitNoteController {

    private final VisitNoteService visitNoteService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 진료 메모 등록 ]",
            description =
                    """
                    **Parameters** visitId, content(필수, 1~5000자, 받아쓰기 결과도 글자로)
                    **Returns** 저장된 메모 {noteId, content, createdAt, updatedAt}. 적은 그대로 저장하고, AI 정리는 자동으로 돌리지 않음.
                    내용이 비었거나 길면 GLOBAL_40001, 없거나 삭제됐거나 남의 일정이면 VISIT_40401
                    """)
    @PostMapping
    public ResponseEntity<BaseResponse<VisitNoteResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long visitId,
            @Valid @RequestBody VisitNoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(201, "진료 메모를 등록했습니다.",
                        visitNoteService.create(principal.userId(), visitId, request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 진료 메모 조회 ]",
            description =
                    """
                    **Parameters** visitId
                    **Returns** 메모 [{noteId, content, createdAt, updatedAt}]. 먼저 쓴 순, 없으면 [].
                    없거나 삭제됐거나 남의 일정이면 VISIT_40401
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<List<VisitNoteResponse>>> list(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long visitId) {
        return ResponseEntity.ok(BaseResponse.success(200, "진료 메모 조회에 성공했습니다.",
                visitNoteService.list(principal.userId(), visitId)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 진료 메모 수정 ]",
            description =
                    """
                    **Parameters** visitId, noteId, content(필수, 1~5000자, 통째로 바뀜)
                    **Returns** 수정된 메모. 내용이 비었거나 길면 GLOBAL_40001, 없거나 삭제됐거나 남의 일정이면 VISIT_40401,
                    없는 메모이거나 다른 일정의 메모면 VISIT_40403
                    """)
    @PatchMapping("/{noteId}")
    public ResponseEntity<BaseResponse<VisitNoteResponse>> update(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long visitId,
            @PathVariable Long noteId, @Valid @RequestBody VisitNoteRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "진료 메모를 수정했습니다.",
                visitNoteService.update(principal.userId(), visitId, noteId, request)));
    }
}
