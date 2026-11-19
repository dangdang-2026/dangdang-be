package com.dangdang.domain.card.controller;

import com.dangdang.domain.card.dto.request.CardCorrectRequest;
import com.dangdang.domain.card.dto.request.CardCreateRequest;
import com.dangdang.domain.card.dto.request.CardReviewRequest;
import com.dangdang.domain.card.dto.response.CardDetailResponse;
import com.dangdang.domain.card.dto.response.CardSummaryResponse;
import com.dangdang.domain.card.service.CardService;
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

@Tag(name = "Card", description = "당당 소식 카드뉴스 API")
@RestController
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    @Operation(summary = "[ 사용자 | 토큰 O | 카드뉴스 목록 ]",
            description = "**Parameters** series(선택: BEFORE_VISIT·GUIDELINE·POSTPARTUM·MIND) **Returns** 최신순, saved(내가 저장했는지). 내려간 카드는 빠짐")
    @GetMapping("/api/cards")
    public ResponseEntity<BaseResponse<List<CardSummaryResponse>>> list(
            @AuthenticationPrincipal AuthPrincipal principal, @RequestParam(required = false) String series) {
        return ResponseEntity.ok(BaseResponse.success(200, "카드 목록 조회에 성공했습니다.",
                cardService.list(principal.userId(), series)));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 카드뉴스 보기 (U-26) ]",
            description = "**Returns** slides·sources·checkedOn·reviewer. CORRECTED면 correctionNote(U-26b), WITHDRAWN이면 내용 없이 withdrawnAt(U-26c). 공감 수 없음")
    @GetMapping("/api/cards/{cardId}")
    public ResponseEntity<BaseResponse<CardDetailResponse>> detail(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long cardId) {
        return ResponseEntity.ok(BaseResponse.success(200, "카드 조회에 성공했습니다.",
                cardService.detail(principal.userId(), cardId)));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 카드 저장 (U-27) ]", description = "두 번 눌러도 한 번. 내려간 카드는 409 CARD_40901")
    @PutMapping("/api/cards/{cardId}/save")
    public ResponseEntity<BaseResponse<CardDetailResponse>> save(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long cardId) {
        return ResponseEntity.ok(BaseResponse.success(200, "카드를 저장했습니다.", cardService.save(principal.userId(), cardId)));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 카드 저장 취소 ]")
    @DeleteMapping("/api/cards/{cardId}/save")
    public ResponseEntity<BaseResponse<Void>> unsave(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long cardId) {
        cardService.unsave(principal.userId(), cardId);
        return ResponseEntity.ok(BaseResponse.success(200, "저장을 취소했습니다.", null));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 저장한 카드 (U-27 · U-27b) ]", description = "최근에 저장한 순. 내려간 카드도 status WITHDRAWN으로 남음. 없으면 []")
    @GetMapping("/api/me/card-saves")
    public ResponseEntity<BaseResponse<List<CardSummaryResponse>>> mySaves(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(BaseResponse.success(200, "저장한 카드 조회에 성공했습니다.",
                cardService.mySaves(principal.userId())));
    }

    // ── 운영자 (/api/admin/** = ADMIN만) ──

    @Operation(summary = "[ 운영자 | 카드 목록 ]", description = "**Returns** 최신순 전체 (DRAFT·RECHECK·WITHDRAWN도 상태 그대로)")
    @GetMapping("/api/admin/cards")
    public ResponseEntity<BaseResponse<List<CardSummaryResponse>>> adminList() {
        return ResponseEntity.ok(BaseResponse.success(200, "카드 목록 조회에 성공했습니다.", cardService.adminList()));
    }

    @Operation(summary = "[ 운영자 | 카드 만들기 (DRAFT) ]", description = "출처 1개 이상·원문 확인일 필수. 공식 지침 문장만")
    @PostMapping("/api/admin/cards")
    public ResponseEntity<BaseResponse<CardDetailResponse>> create(@Valid @RequestBody CardCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(201, "카드를 만들었습니다.",
                cardService.create(request)));
    }

    @Operation(summary = "[ 운영자 | 카드 올리기 ]", description = "DRAFT → PUBLISHED. 다른 상태면 409 CARD_40902")
    @PostMapping("/api/admin/cards/{cardId}/publish")
    public ResponseEntity<BaseResponse<CardDetailResponse>> publish(@PathVariable Long cardId) {
        return ResponseEntity.ok(BaseResponse.success(200, "카드를 올렸습니다.", cardService.publish(cardId)));
    }

    @Operation(summary = "[ 운영자 | 카드 정정 (U-26b) ]", description = "**Parameters** note(필수) · body · checkedOn → CORRECTED")
    @PostMapping("/api/admin/cards/{cardId}/correct")
    public ResponseEntity<BaseResponse<CardDetailResponse>> correct(
            @PathVariable Long cardId, @Valid @RequestBody CardCorrectRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "카드를 정정했습니다.", cardService.correct(cardId, request)));
    }

    @Operation(summary = "[ 운영자 | 검수자 적기 ]", description = "**Parameters** reviewer · reviewedOn (둘 다 필수). 내려간 카드면 409 CARD_40902")
    @PostMapping("/api/admin/cards/{cardId}/review")
    public ResponseEntity<BaseResponse<CardDetailResponse>> review(
            @PathVariable Long cardId, @Valid @RequestBody CardReviewRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "검수자를 적었습니다.", cardService.review(cardId, request)));
    }

    @Operation(summary = "[ 운영자 | 카드 내리기 (U-26c) ]", description = "→ WITHDRAWN. 목록에서 빠지고 저장한 사람에겐 「내려간 카드」. 이 카드를 가리키는 소식 카드도 안 보임")
    @PostMapping("/api/admin/cards/{cardId}/withdraw")
    public ResponseEntity<BaseResponse<CardDetailResponse>> withdraw(@PathVariable Long cardId) {
        return ResponseEntity.ok(BaseResponse.success(200, "카드를 내렸습니다.", cardService.withdraw(cardId)));
    }
}
