package com.dangdang.domain.card.service;

import com.dangdang.domain.admin.enums.AdminLogAction;
import com.dangdang.domain.admin.service.AdminLogService;
import com.dangdang.domain.announcement.service.AnnouncementService;
import com.dangdang.domain.card.dto.request.CardCorrectRequest;
import com.dangdang.domain.card.dto.request.CardCreateRequest;
import com.dangdang.domain.card.dto.request.CardReviewRequest;
import com.dangdang.domain.card.dto.response.AdminCardItemResponse;
import com.dangdang.domain.card.dto.response.CardDetailResponse;
import com.dangdang.domain.card.dto.response.CardSummaryResponse;
import com.dangdang.domain.card.entity.ContentCard;
import com.dangdang.domain.card.enums.CardStatus;
import com.dangdang.domain.card.exception.CardErrorCode;
import com.dangdang.domain.card.repository.CardReactionRepository;
import com.dangdang.domain.card.repository.CardSaveRepository;
import com.dangdang.domain.card.repository.ContentCardRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

// 당당 소식 카드뉴스 (COM-12 · U-26~U-28). 공식 지침 문장만, 출처·확인일 필수, 카드 안 광고 없음
@Service
@RequiredArgsConstructor
public class CardService {

    private final ContentCardRepository contentCardRepository;
    private final CardSaveRepository cardSaveRepository;
    private final CardReactionRepository cardReactionRepository;
    private final AnnouncementService announcementService;
    private final AdminLogService adminLogService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<CardSummaryResponse> list(Long userId, String series) {
        List<ContentCard> cards = series == null
                ? contentCardRepository.findByStatusInOrderByCreatedAtDescIdDesc(CardStatus.LIVE)
                : contentCardRepository.findBySeriesAndStatusInOrderByCreatedAtDescIdDesc(series, CardStatus.LIVE);
        Set<Long> saved = cards.isEmpty() ? Set.of()
                : cardSaveRepository.findSavedCardIds(userId, cards.stream().map(ContentCard::getId).toList());
        return cards.stream().map(c -> CardSummaryResponse.of(c, saved.contains(c.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public CardDetailResponse detail(Long userId, Long cardId) {
        ContentCard card = visible(cardId);
        return CardDetailResponse.of(card, cardSaveRepository.existsByUserIdAndCardId(userId, cardId),
                cardReactionRepository.existsByUserIdAndCardId(userId, cardId));
    }

    // 저장 (U-27) — 두 번 눌러도, 동시에 눌러도 한 줄 (ON CONFLICT DO NOTHING)
    // 카드 줄을 공유 잠금으로 읽어서, 운영자가 내리는 중이면 끝난 뒤의 상태(WITHDRAWN)를 보고 409
    @Transactional
    public CardDetailResponse save(Long userId, Long cardId) {
        ContentCard card = contentCardRepository.findByIdForShare(cardId)
                .filter(ContentCard::isVisible)
                .orElseThrow(() -> new CustomException(CardErrorCode.CARD_NOT_FOUND));
        if (card.getStatus() == CardStatus.WITHDRAWN) {
            throw new CustomException(CardErrorCode.WITHDRAWN);
        }
        cardSaveRepository.addIfAbsent(userId, cardId, clock.instant());
        return CardDetailResponse.of(card, true, cardReactionRepository.existsByUserIdAndCardId(userId, cardId));
    }

    @Transactional
    public void unsave(Long userId, Long cardId) {
        cardSaveRepository.remove(userId, cardId);
    }

    // 공감 — 저장과 같은 방식 (두 번 눌러도 한 줄, 내려간 카드는 409). 숫자는 돌려주지 않는다
    @Transactional
    public CardDetailResponse react(Long userId, Long cardId) {
        ContentCard card = contentCardRepository.findByIdForShare(cardId)
                .filter(ContentCard::isVisible)
                .orElseThrow(() -> new CustomException(CardErrorCode.CARD_NOT_FOUND));
        if (card.getStatus() == CardStatus.WITHDRAWN) {
            throw new CustomException(CardErrorCode.WITHDRAWN_REACT);
        }
        cardReactionRepository.addIfAbsent(userId, cardId, clock.instant());
        return CardDetailResponse.of(card, cardSaveRepository.existsByUserIdAndCardId(userId, cardId), true);
    }

    @Transactional
    public void unreact(Long userId, Long cardId) {
        cardReactionRepository.remove(userId, cardId);
    }

    // 저장한 카드 (U-27 · 빈 상태 U-27b) — 내려간 카드도 「내려간 카드」로 남긴다
    @Transactional(readOnly = true)
    public List<CardSummaryResponse> mySaves(Long userId) {
        return cardSaveRepository.findMine(userId).stream()
                .filter(s -> s.getCard().isVisible())
                .map(s -> CardSummaryResponse.of(s.getCard(), true)).toList();
    }

    // ── 운영자 ──

    // 운영자 목록 — 쓰는 중 카드의 id도 여기서 다시 찾는다
    @Transactional(readOnly = true)
    public List<AdminCardItemResponse> adminList() {
        return contentCardRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(AdminCardItemResponse::of).toList();
    }

    // 운영자 상세 — 정정할 때 지금 슬라이드를 불러와 고친 장만 바꿔 보낸다
    public CardDetailResponse adminDetail(Long cardId) {
        return CardDetailResponse.forAdmin(contentCardRepository.findById(cardId)
                .orElseThrow(() -> new CustomException(CardErrorCode.CARD_NOT_FOUND)));
    }

    @Transactional
    public CardDetailResponse create(Long adminId, CardCreateRequest req) {
        ContentCard card = contentCardRepository.save(ContentCard.draft(req.title().strip(), req.series(),
                req.body(), req.sources(), strip(req.questions()), req.checkedOn(), clock.instant()));
        adminLogService.record(adminId, AdminLogAction.CARD_CREATE, card.getId(), card.getTitle(), null, card.getBody().size() + "장");
        return CardDetailResponse.of(card);
    }

    @Transactional
    public CardDetailResponse publish(Long adminId, Long cardId) {
        ContentCard card = locked(cardId);
        if (card.getStatus() != CardStatus.DRAFT) {
            throw new CustomException(CardErrorCode.WRONG_STATUS);
        }
        card.publish(clock.instant());
        adminLogService.record(adminId, AdminLogAction.CARD_PUBLISH, cardId, card.getTitle(), null, null);
        return CardDetailResponse.of(card);
    }

    @Transactional
    public CardDetailResponse correct(Long adminId, Long cardId, CardCorrectRequest req) {
        ContentCard card = locked(cardId);
        if (card.getStatus() == CardStatus.DRAFT || card.getStatus() == CardStatus.WITHDRAWN) {
            throw new CustomException(CardErrorCode.WRONG_STATUS);
        }
        card.correct(req.body(), strip(req.questions()), req.summary().strip(),
                req.note() == null || req.note().isBlank() ? null : req.note().strip(), req.checkedOn(), clock.instant());
        adminLogService.record(adminId, AdminLogAction.CARD_CORRECT, cardId, card.getTitle(), req.summary().strip(), null);
        return CardDetailResponse.of(card);
    }

    @Transactional
    public CardDetailResponse withdraw(Long adminId, Long cardId) {
        ContentCard card = locked(cardId);
        if (card.getStatus() == CardStatus.DRAFT || card.getStatus() == CardStatus.WITHDRAWN) {
            throw new CustomException(CardErrorCode.WRONG_STATUS);
        }
        Instant now = clock.instant();
        card.withdraw(now);
        // 기록도 벌크 UPDATE(endAllForCard) 전에 남긴다 — 그 뒤엔 영속성 컨텍스트가 비워진다
        adminLogService.record(adminId, AdminLogAction.CARD_WITHDRAW, cardId, card.getTitle(), null, null);
        // 이 카드를 건 소식(스플래시·커뮤니티 맨 위)도 같은 시각으로 끝낸다. findActive가 LIVE 카드만 보여 줘서
        // 화면엔 안 보이지만, 안 끝내면 줄이 열린 채 남아 카드가 다시 올라갈 때 옛 소식이 되살아난다.
        // 카드 줄을 잠근 같은 트랜잭션이라 내리기가 롤백되면 같이 롤백된다 (Redis·푸시 없음 → afterCommit 필요 없음)
        // 엔티티 변경(card.withdraw 등)은 이 호출 전에 한다. 벌크 UPDATE가 영속성 컨텍스트를 비워서, 뒤에 card를
        // 고치면 detached라 저장되지 않는다 (아래 응답 만들기처럼 읽기만은 괜찮다)
        announcementService.endAllForCard(cardId, now);
        return CardDetailResponse.of(card);
    }

    // 검수자 적기 (U-26 「검수자 · 검수일」) — 내려간 카드는 안 됨
    @Transactional
    public CardDetailResponse review(Long adminId, Long cardId, CardReviewRequest req) {
        ContentCard card = locked(cardId);
        if (card.getStatus() == CardStatus.WITHDRAWN) {
            throw new CustomException(CardErrorCode.WRONG_STATUS);
        }
        card.review(req.reviewer().strip(), req.reviewedOn(), clock.instant());
        adminLogService.record(adminId, AdminLogAction.CARD_REVIEW, cardId, card.getTitle(), null,
                "검수 " + req.reviewer().strip() + " · " + req.reviewedOn());
        return CardDetailResponse.of(card);
    }

    private static List<String> strip(List<String> questions) {
        return questions == null ? null : questions.stream().map(String::strip).toList();
    }

    // 사용자에겐 쓰는 중(DRAFT) 카드는 없는 카드
    private ContentCard visible(Long cardId) {
        ContentCard card = any(cardId);
        if (!card.isVisible()) {
            throw new CustomException(CardErrorCode.CARD_NOT_FOUND);
        }
        return card;
    }

    private ContentCard any(Long cardId) {
        return contentCardRepository.findById(cardId)
                .orElseThrow(() -> new CustomException(CardErrorCode.CARD_NOT_FOUND));
    }

    // 운영자가 상태를 바꿀 때 — 행을 잠그고 읽는다 (동시에 정정·내리기 해도 차례로)
    private ContentCard locked(Long cardId) {
        return contentCardRepository.findByIdForUpdate(cardId)
                .orElseThrow(() -> new CustomException(CardErrorCode.CARD_NOT_FOUND));
    }
}
