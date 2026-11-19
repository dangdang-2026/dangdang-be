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

// 스플래시·커뮤니티 카드 (AD-01·AD-02). 「하루 1번·최대 1.2초」는 프론트가 정한다 — 서버는 지금 걸린 것만 준다
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final ContentCardRepository contentCardRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> active(AnnouncementSlot slot) {
        return announcementRepository.findActive(slot, clock.instant(), CardStatus.LIVE, PageRequest.of(0, slot.getMaxShown())).stream()
                .map(AnnouncementResponse::from).toList();
    }

    @Transactional
    public AnnouncementResponse create(AnnouncementCreateRequest req) {
        String linkUrl = req.linkUrl();
        if (req.contentCardId() != null) {
            ContentCard card = contentCardRepository.findById(req.contentCardId())
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
        return AnnouncementResponse.from(saved);
    }

    @Transactional
    public AnnouncementResponse end(Long id) {
        Announcement a = announcementRepository.findById(id)
                .orElseThrow(() -> new CustomException(AnnouncementErrorCode.ANNOUNCEMENT_NOT_FOUND));
        a.end(clock.instant());
        return AnnouncementResponse.from(a);
    }
}
