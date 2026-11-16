package com.dangdang.domain.chat.repository;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.reading.entity.Reading;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

// 챗봇 기록 카드용 혈당 조회 (승민 언니 ReadingRepository를 안 고치려고 따로 둔다). 읽기만 한다
@Repository
@RequiredArgsConstructor
public class ChatRecordsQuery {

    private final EntityManager em;

    private static final String WHERE = " from Reading r where r.user.id = :userId and r.deletedAt is null"
            + " and r.measuredAt >= :from and r.measuredAt < :to";

    // 최신 기록부터 limit개 (넘치면 오래된 쪽이 잘린다, 리뷰 #79)
    public List<Reading> latestReadings(Long userId, Instant from, Instant to, MeasureSlot slotOrNull, int limit) {
        String jpql = "select r" + WHERE + (slotOrNull == null ? "" : " and r.slot = :slot")
                + " order by r.measuredAt desc, r.id desc";
        var query = em.createQuery(jpql, Reading.class)
                .setParameter("userId", userId)
                .setParameter("from", from)
                .setParameter("to", to)
                .setMaxResults(limit);
        if (slotOrNull != null) {
            query.setParameter("slot", slotOrNull);
        }
        return query.getResultList();
    }

    // 기간 안 진짜 개수 (카드의 count)
    public long count(Long userId, Instant from, Instant to, MeasureSlot slotOrNull) {
        String jpql = "select count(r)" + WHERE + (slotOrNull == null ? "" : " and r.slot = :slot");
        var query = em.createQuery(jpql, Long.class)
                .setParameter("userId", userId)
                .setParameter("from", from)
                .setParameter("to", to);
        if (slotOrNull != null) {
            query.setParameter("slot", slotOrNull);
        }
        return query.getSingleResult();
    }
}
