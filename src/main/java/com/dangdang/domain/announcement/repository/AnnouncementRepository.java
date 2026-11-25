package com.dangdang.domain.announcement.repository;

import com.dangdang.domain.announcement.entity.Announcement;
import com.dangdang.domain.announcement.enums.AnnouncementSlot;
import com.dangdang.domain.card.enums.CardStatus;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    // 지금 걸려 있는 것 — 당당 소식이 먼저, 그다음 최근에 시작한 순. 개수는 자리마다 정한 만큼만 (pageable)
    // 카드를 가리키는 소식은 그 카드가 아직 올라가 있을 때만 (내린 카드를 스플래시에 띄우지 않게).
    // content_cards는 지우지 않고 상태만 바꾸니 FK의 ON DELETE SET NULL이 안 돈다 → 여기서 거른다
    @Query("select a from Announcement a where a.slot = :slot and a.activeFrom <= :now"
            + " and (a.activeTo is null or a.activeTo > :now)"
            + " and (a.contentCardId is null or exists ("
            + "     select c.id from ContentCard c where c.id = a.contentCardId and c.status in :live))"
            + " order by case when a.kind = com.dangdang.domain.announcement.enums.AnnouncementKind.NEWS then 0 else 1 end,"
            + " a.activeFrom desc, a.id desc")
    List<Announcement> findActive(@Param("slot") AnnouncementSlot slot, @Param("now") Instant now,
                                  @Param("live") Collection<CardStatus> live, Pageable pageable);

    // 운영자가 소식 하나를 내릴 때. 아래 endAllForCard(카드 내리기)와 같은 줄을 고칠 수 있어서 잠가서 읽는다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Announcement a where a.id = :id")
    Optional<Announcement> findByIdForUpdate(@Param("id") Long id);

    // 카드를 내리면 그 카드를 건 소식도 끝낸다 (안 끝내면 카드가 다시 올라갈 때 옛 소식이 저절로 되살아난다)
    // 끝 시각 규칙은 Announcement.end와 같다: 시작 전이면 끝 = 시작, 이미 그보다 일찍 끝난 줄은 그대로
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Announcement a set a.activeTo = case when a.activeFrom > :now then a.activeFrom else :now end"
            + " where a.contentCardId = :cardId"
            + " and (a.activeTo is null or (a.activeTo > :now and a.activeTo > a.activeFrom))")
    int endAllForCard(@Param("cardId") Long cardId, @Param("now") Instant now);
}
