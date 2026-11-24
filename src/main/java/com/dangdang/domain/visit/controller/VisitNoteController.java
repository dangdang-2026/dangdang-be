package com.dangdang.domain.visit.controller;

import com.dangdang.domain.visit.dto.request.VisitNoteRequest;
import com.dangdang.domain.visit.dto.response.VisitNoteOrganizeResponse;
import com.dangdang.domain.visit.dto.response.VisitNoteResponse;
import com.dangdang.domain.visit.service.VisitNoteOrganizeService;
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
    private final VisitNoteOrganizeService visitNoteOrganizeService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 진료 메모 등록 ]",
            description =
                    """
                    **Parameters** visitId, content(필수, 1~5000자, 받아쓰기 결과도 글자로)
                    **Returns** 저장된 메모 {noteId, content, aiExtract(처음엔 null), createdAt, updatedAt}. 적은 그대로 저장하고, AI 정리는 자동으로 돌리지 않음.
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
                    **Returns** 메모 [{noteId, content, aiExtract(AI 정리 결과, 없으면 null), createdAt, updatedAt}]. 먼저 쓴 순, 없으면 [].
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
                    **Returns** 수정된 메모. 내용이 바뀌면 aiExtract는 비워짐(옛 메모 기준이라). 내용이 비었거나 길면 GLOBAL_40001, 없거나 삭제됐거나 남의 일정이면 VISIT_40401,
                    없는 메모이거나 다른 일정의 메모면 VISIT_40403
                    """)
    @PatchMapping("/{noteId}")
    public ResponseEntity<BaseResponse<VisitNoteResponse>> update(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long visitId,
            @PathVariable Long noteId, @Valid @RequestBody VisitNoteRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "진료 메모를 수정했습니다.",
                visitNoteService.update(principal.userId(), visitId, noteId, request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 진료 메모 AI 정리 ]",
            description =
                    """
                    **Parameters** visitId, noteId (본문 없음)
                    **Returns** {noteId, aiExtract{measureSlots, fastingTarget, postMealTarget, postMealMinutes, nextVisitDate, insulinSkipped, promptVersion}}.
                    칸마다 {value, evidence(AI로 보낸 글의 근거 문장 그대로 — [이름]·[병원]·[전화번호]가 보일 수 있음), status(FOUND 체크된 채로 / CHECK_NEEDED 「확인 필요」+체크 해제)}, 메모에 없는 칸은 null(화면에 안 보임).
                    measureSlots.value는 FASTING·BREAKFAST_AFTER·LUNCH_AFTER·DINNER_AFTER·BEDTIME, nextVisitDate.value는 yyyy-MM-dd.
                    postMealMinutes·postMealTarget은 짝(「식후 기준」 카드 하나): 하나만 찾았거나 하나라도 CHECK_NEEDED면 찾은 칸 모두 CHECK_NEEDED.
                    AI 서버로 보내기 전에 내 부를 이름·커뮤니티 닉네임·등록한 병원 이름(지운 병원 포함)·전화번호를 [이름]·[병원]·[전화번호]로 바꿔 보냄 (저장된 메모는 그대로, 두 글자 이상만. 남의 이름·줄여 쓴 병원 이름은 못 가리고, 두 글자 이름이 「혈당지수」처럼 낱말 안에 있으면 안 가림).
                    인슐린·약 문장은 AI 서버가 걸러 내서 처방·용량은 뽑지 않음. 걸러 낸 문장이 있으면 insulinSkipped=true → 「약은 직접 적어 주세요」.
                    결과는 메모에 저장되고 다시 정리하면 바뀜.
                    체크한 기준은 POST /api/criteria-versions, 다음 진료일은 POST /api/visits로 보냄 (이 API는 적용하지 않음).
                    진료 날짜가 미정인 일정이면 오늘을 진료일로 보고 정리함.
                    국외 이전 동의가 없으면 AI_40301, 오늘 10번을 넘으면 AI_42901, 메모가 3000자를 넘으면 VISIT_40002,
                    정리하는 동안 메모가 바뀌면 VISIT_40901, AI 실패·시간 초과면 VISIT_50201(횟수는 안 셈, 다시 해 보기),
                    없거나 삭제됐거나 남의 일정이면 VISIT_40401, 없는 메모이거나 다른 일정의 메모면 VISIT_40403
                    """)
    @PostMapping("/{noteId}/organize")
    public ResponseEntity<BaseResponse<VisitNoteOrganizeResponse>> organize(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long visitId,
            @PathVariable Long noteId) {
        return ResponseEntity.ok(BaseResponse.success(200, "진료 메모를 정리했습니다.",
                visitNoteOrganizeService.organize(principal.userId(), visitId, noteId)));
    }
}
