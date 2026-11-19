package com.dangdang.domain.card.repository;

import com.dangdang.domain.card.entity.ContentCard;
import com.dangdang.domain.card.enums.CardStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ContentCardRepository extends JpaRepository<ContentCard, Long> {

    List<ContentCard> findByStatusInOrderByCreatedAtDescIdDesc(Collection<CardStatus> statuses);

    List<ContentCard> findBySeriesAndStatusInOrderByCreatedAtDescIdDesc(String series, Collection<CardStatus> statuses);

    // 운영자 목록 — 쓰는 중(DRAFT)·내려간 카드까지 전부
    List<ContentCard> findAllByOrderByCreatedAtDescIdDesc();

    boolean existsByIdAndStatusIn(Long id, Collection<CardStatus> statuses);

    // 운영자가 상태를 바꿀 때 — 행을 잠가서 두 운영자가 동시에 정정·내리기를 해도 차례로 처리
    // (안 잠그면 내린 카드가 나중에 커밋된 정정으로 CORRECTED로 되살아날 수 있다)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ContentCard c where c.id = :id")
    Optional<ContentCard> findByIdForUpdate(@Param("id") Long id);
}
