package com.dangdang.domain.announcement.repository;

import com.dangdang.domain.announcement.entity.Announcement;
import com.dangdang.domain.announcement.enums.AnnouncementSlot;
import com.dangdang.domain.card.enums.CardStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    // 지금 걸려 있는 것 — 당당 소식이 먼저, 그다음 최근에 시작한 순
    // 카드를 가리키는 소식은 그 카드가 아직 올라가 있을 때만 (내린 카드를 스플래시에 띄우지 않게).
    // content_cards는 지우지 않고 상태만 바꾸니 FK의 ON DELETE SET NULL이 안 돈다 → 여기서 거른다
    @Query("select a from Announcement a where a.slot = :slot and a.activeFrom <= :now"
            + " and (a.activeTo is null or a.activeTo > :now)"
            + " and (a.contentCardId is null or exists ("
            + "     select c.id from ContentCard c where c.id = a.contentCardId and c.status in :live))"
            + " order by case when a.kind = com.dangdang.domain.announcement.enums.AnnouncementKind.NEWS then 0 else 1 end,"
            + " a.activeFrom desc, a.id desc")
    List<Announcement> findActive(@Param("slot") AnnouncementSlot slot, @Param("now") Instant now,
                                  @Param("live") Collection<CardStatus> live);
}
