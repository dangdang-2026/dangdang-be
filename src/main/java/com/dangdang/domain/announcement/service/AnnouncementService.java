package com.dangdang.domain.announcement.service;

import com.dangdang.domain.announcement.dto.request.AnnouncementCreateRequest;
import com.dangdang.domain.announcement.dto.response.AnnouncementResponse;
import com.dangdang.domain.announcement.entity.Announcement;
import com.dangdang.domain.announcement.enums.AnnouncementSlot;
import com.dangdang.domain.announcement.exception.AnnouncementErrorCode;
import com.dangdang.domain.announcement.repository.AnnouncementRepository;
import com.dangdang.domain.card.entity.ContentCard;
import com.dangdang.domain.card.enums.CardStatus;
import com.dangdang.domain.card.exception.CardErrorCode;
import com.dangdang.domain.card.repository.ContentCardRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

// 스플래시·커뮤니티 카드 (AD-01·AD-02). 「하루 1번·최대 1.2초」는 프론트가 정한다 — 서버는 지금 걸린 것만 준다
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final ContentCardRepository contentCardRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> active(AnnouncementSlot slot) {
        List<Announcement> found = announcementRepository.findActive(slot, clock.instant(), CardStatus.LIVE,
                PageRequest.of(0, slot.getMaxShown()));
        // 연결된 카드는 한 번에 읽는다 (소식 n개면 쿼리 n번 → 1번)
        List<Long> cardIds = found.stream().map(Announcement::getContentCardId).filter(Objects::nonNull).toList();
        Map<Long, ContentCard> cards = cardIds.isEmpty() ? Map.of()
                : contentCardRepository.findAllById(cardIds).stream()
                        .collect(Collectors.toMap(ContentCard::getId, Function.identity()));
        return found.stream()
                .map(a -> AnnouncementResponse.from(a, a.getContentCardId() == null ? null : cards.get(a.getContentCardId())))
                .toList();
    }

    @Transactional
    public AnnouncementResponse create(AnnouncementCreateRequest req) {
        String linkUrl = req.linkUrl();
        ContentCard card = null;
        if (req.contentCardId() != null) {
            // 카드 줄을 공유 잠금으로 읽는다. 운영자가 그 카드를 내리는 중이면 끝난 뒤의 상태를 보고 막는다
            // (안 잠그면 내리기의 endAllForCard가 아직 안 들어온 이 소식을 못 보고, 내린 카드에 열린 소식이 남는다)
            card = contentCardRepository.findByIdForShare(req.contentCardId())
                    .orElseThrow(() -> new CustomException(CardErrorCode.CARD_NOT_FOUND));
            // 쓰는 중(DRAFT)·내려간 카드를 걸면 스플래시에서 눌렀을 때 404·「내려간 카드」가 뜬다
            if (!CardStatus.LIVE.contains(card.getStatus())) {
                throw new CustomException(AnnouncementErrorCode.CARD_NOT_LIVE);
            }
            // 거는 카드와 누르면 가는 곳이 어긋나지 않게 링크는 서버가 정한다
            linkUrl = "/cards/" + card.getId();
        }
        Instant now = clock.instant();
        Instant from = req.activeFrom() == null ? now : req.activeFrom();
        if (req.activeTo() != null && !req.activeTo().isAfter(from)) {   // 시작을 안 보내면(=지금) DTO 검사로는 못 잡는다
            throw new CustomException(AnnouncementErrorCode.INVALID_PERIOD);
        }
        Announcement saved = announcementRepository.save(Announcement.of(req.slot(), req.kind(), req.title().strip(),
                req.imageUrl(), linkUrl, req.contentCardId(), from, req.activeTo(),
                req.reviewedBy() == null ? null : req.reviewedBy().strip(), now));
        return AnnouncementResponse.from(saved, card);
    }

    @Transactional
    public AnnouncementResponse end(Long id) {
        Announcement a = announcementRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CustomException(AnnouncementErrorCode.ANNOUNCEMENT_NOT_FOUND));
        a.end(clock.instant());
        // 그 사이 카드가 내려갔으면 요약을 비운다 — 목록(active)과 같은 결과 (PR #120 리뷰)
        return AnnouncementResponse.from(a, a.getContentCardId() == null ? null
                : contentCardRepository.findById(a.getContentCardId())
                        .filter(c -> CardStatus.LIVE.contains(c.getStatus())).orElse(null));
    }

    // 카드를 내릴 때 같이 부른다 (CardService.withdraw, 카드 줄을 잠근 같은 트랜잭션)
    // now는 카드를 내린 시각(앱 시계)을 그대로 받아서, 카드 withdrawnAt과 소식 끝 시각이 같게
    @Transactional
    public void endAllForCard(Long cardId, Instant now) {
        announcementRepository.endAllForCard(cardId, now);
    }
}
