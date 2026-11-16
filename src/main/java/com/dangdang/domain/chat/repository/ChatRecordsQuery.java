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

    public List<Reading> readings(Long userId, Instant from, Instant to, MeasureSlot slotOrNull, int limit) {
        String jpql = "select r from Reading r where r.user.id = :userId and r.deletedAt is null"
                + " and r.measuredAt >= :from and r.measuredAt < :to"
                + (slotOrNull == null ? "" : " and r.slot = :slot")
                + " order by r.measuredAt asc, r.id asc";
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
}
