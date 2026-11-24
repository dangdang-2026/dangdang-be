package com.dangdang.domain.chat.controller;

import com.dangdang.domain.chat.dto.request.ChatAskRequest;
import com.dangdang.domain.chat.dto.request.ChatFeedbackRequest;
import com.dangdang.domain.chat.dto.response.ChatAskResponse;
import com.dangdang.domain.chat.dto.response.ChatMessageResponse;
import com.dangdang.domain.chat.dto.response.ChatPageResponse;
import com.dangdang.domain.chat.service.ChatService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Chat", description = "당당이에게 물어보기 (챗봇) API")
@Validated
@RestController
@RequestMapping("/api/chat/messages")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 당당이에게 물어보기 ]",
            description =
                    """
                    **Parameters** content(필수, 500자)
                    **Returns** question(보낸 말) · answer(당당이 답). answer.kind로 화면을 고른다:
                    RECORDS(Q-03 기록 카드 records) · QUESTION_DRAFT(Q-04 questionDraft) · INFO(Q-06 출처 sources) · UNKNOWN(병원에 물어보세요)
                    RISK(Q-07 위급 안내 · actions 전화 버튼) · DOSE(약 용량 → 병원) · CONSENT_NEEDED(기록 사용 동의 필요) · FAILED(Q-08 다시 묻기)
                    RISK·DOSE는 말 목록으로 먼저 잡으면 AI를 거치지 않아서 국외 이전 동의·하루 상한과 상관없이 온다
                    RISK면 risk{type URGENT·HYPO·MIND·SAFETY, title, warning}, content는 본문만. 버튼은 actions(CALL_HOSPITAL·CALL_119·CALL_109·CALL_1577_0199·CALL_112·CALL_1366)
                    AI가 위급·마음 위기·폭력 이유를 고르면 같은 RISK 안내로 바꿔서 준다 (이때는 AI를 불렀으니 횟수에 셈)
                    UNKNOWN이면 reason(JUDGMENT·NO_SOURCE·CHITCHAT·RUDE·OFF_TOPIC·NO_CONSENT·DISTRESS·APP_HELP·ABOUT)으로 버튼을 고른다
                    건강정보 동의(SENSITIVE)가 없으면 403 USER_40302 (아무것도 저장 안 함, 위험 신호·약 용량도). 국외 이전 동의(OVERSEAS_TRANSFER)가 없으면 403 AI_40301 (위험 신호·약 용량은 이 동의 없이도 옴). 내 기록 답은 AI_CHAT_RECORDS 동의가 더 있어야 한다. 하루 30번 넘으면 429 AI_42901 (FAILED는 횟수에 안 셈)
                    """)
    @PostMapping
    public ResponseEntity<BaseResponse<ChatAskResponse>> ask(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody ChatAskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(201, "당당이가 답했어요.",
                chatService.ask(principal.userId(), request.content().strip())));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 대화 목록 ]",
            description = "**Parameters** cursorId(첫 장은 비움, 다음 장은 받은 nextCursorId), size(기본 20, 1~50) "
                    + "**Returns** items(최신이 먼저), size, hasNext, nextCursorId. "
                    + "items[].risk(RISK 위급 안내 틀)·reason(UNKNOWN 이유)은 물어보기 답과 같고, 예전 대화는 null일 수 있다")
    @GetMapping
    public ResponseEntity<BaseResponse<ChatPageResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) @Min(1) Long cursorId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(BaseResponse.success(200, "대화 목록 조회에 성공했습니다.",
                chatService.list(principal.userId(), cursorId, size)));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 대화 모두 지우기 ]", description = "**Returns** deletedCount. 바로 지워지고 되돌릴 수 없다")
    @DeleteMapping
    public ResponseEntity<BaseResponse<Map<String, Integer>>> deleteAll(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(BaseResponse.success(200, "대화를 지웠습니다.",
                Map.of("deletedCount", chatService.deleteAll(principal.userId()))));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 도움이 됐나요 ]",
            description = "**Parameters** helpful(true/false) **Returns** 그 답. 내가 쓴 말이면 400 CHAT_40001, 남의 것·없는 번호 404 CHAT_40401")
    @PatchMapping("/{messageId}/feedback")
    public ResponseEntity<BaseResponse<ChatMessageResponse>> rate(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long messageId,
            @Valid @RequestBody ChatFeedbackRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "평가를 저장했습니다.",
                chatService.rate(principal.userId(), messageId, request.helpful())));
    }
}
