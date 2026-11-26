package com.dangdang.domain.admin.service;

import com.dangdang.domain.admin.dto.response.AdminAnnouncementListResponse;
import com.dangdang.domain.admin.dto.response.AdminAnnouncementListResponse.Item;
import com.dangdang.domain.admin.dto.response.AdminAnnouncementListResponse.LinkedCard;
import com.dangdang.domain.admin.dto.response.AdminAnnouncementListResponse.Slot;
import com.dangdang.domain.admin.enums.AnnouncementState;
import com.dangdang.domain.announcement.entity.Announcement;
import com.dangdang.domain.announcement.enums.AnnouncementSlot;
import com.dangdang.domain.announcement.repository.AnnouncementRepository;
import com.dangdang.domain.card.entity.ContentCard;
import com.dangdang.domain.card.repository.ContentCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

// 운영자 당당 소식 목록. 앱용 GET /api/announcements 는 지금 걸린 것만 주니, 예약·끝난 것까지 여기서 본다.
// 소식은 많아야 수십 개라 한 번에 읽고 자바에서 나눈다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAnnouncementQueryService {

    private final AnnouncementRepository announcementRepository;
    private final ContentCardRepository contentCardRepository;
    private final Clock clock;

    // state 가 null 이면 전체. ENDED 탭은 예약 취소도 같이
    public AdminAnnouncementListResponse list(AnnouncementState state) {
        Instant now = clock.instant();
        List<Announcement> all = announcementRepository.findAll();

        List<Long> cardIds = all.stream().map(Announcement::getContentCardId).filter(Objects::nonNull).distinct().toList();
        Map<Long, ContentCard> cards = cardIds.isEmpty() ? Map.of()
                : contentCardRepository.findAllById(cardIds).stream()
                        .collect(Collectors.toMap(ContentCard::getId, Function.identity()));

        List<Item> items = all.stream()
                .map(a -> item(a, a.getContentCardId() == null ? null : cards.get(a.getContentCardId()), now))   // Map.of는 null 키를 못 받는다
                .filter(i -> state == null || i.state() == state || (state == AnnouncementState.ENDED && i.state().isOver()))
                .sorted(ORDER)
                .toList();

        List<Item> every = all.stream().map(a -> item(a, null, now)).toList();
        long live = every.stream().filter(i -> i.state() == AnnouncementState.LIVE).count();
        long scheduled = every.stream().filter(i -> i.state() == AnnouncementState.SCHEDULED).count();
        long ended = every.stream().filter(i -> i.state().isOver()).count();
        List<Slot> slots = Arrays.stream(AnnouncementSlot.values()).map(s -> {
            long used = every.stream().filter(i -> i.slot() == s && i.state() == AnnouncementState.LIVE).count();
            return new Slot(s, used, s.getMaxShown(), used >= s.getMaxShown());
        }).toList();
        return new AdminAnnouncementListResponse(live, scheduled, ended, slots, items);
    }

    // 올라가 있음(최근에 시작한 것부터) → 예약(먼저 올라갈 것부터) → 끝남(최근에 끝난 것부터) → 예약 취소.
    // 예약 취소는 끝 시각이 미래의 시작 시각이라 끝난 순서로 세우면 맨 앞에 온다 → 따로 맨 뒤에 둔다
    private static final Comparator<Item> ORDER = Comparator
            .comparingInt((Item i) -> switch (i.state()) {
                case LIVE -> 0;
                case SCHEDULED -> 1;
                case ENDED -> 2;
                case CANCELED -> 3;
            })
            .thenComparing((a, b) -> switch (a.state()) {
                case LIVE -> b.activeFrom().compareTo(a.activeFrom());
                case SCHEDULED -> a.activeFrom().compareTo(b.activeFrom());
                case ENDED, CANCELED -> b.activeTo().compareTo(a.activeTo());
            })
            .thenComparing(Item::id, Comparator.reverseOrder());

    private static Item item(Announcement a, ContentCard card, Instant now) {
        LinkedCard linked = card == null ? null
                : new LinkedCard(card.getId(), card.getTitle(), card.getBody().size(), card.getSources().size());
        return new Item(a.getId(), a.getSlot(), a.getKind(), a.getTitle(), a.getImageUrl(), a.getLinkUrl(), linked,
                a.getActiveFrom(), a.getActiveTo(), a.getReviewedBy(),
                AnnouncementState.of(a.getActiveFrom(), a.getActiveTo(), now));
    }
}
