package com.dangdang.domain.visit.repository;

import com.dangdang.domain.visit.entity.VisitNote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// 진료 메모 DB 저장·조회
public interface VisitNoteRepository extends JpaRepository<VisitNote, Long> {

    // 이 일정의 살아 있는 메모 전부: 먼저 쓴 순
    List<VisitNote> findAllByVisitIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(Long visitId);

    // 이 일정에 붙은 이 메모 + 삭제 안 된 것만 (다른 일정의 메모 id로는 못 찾음)
    Optional<VisitNote> findByIdAndVisitIdAndDeletedAtIsNull(Long id, Long visitId);

    // 위와 같은 조건으로 찾으면서 이 메모 줄을 잠금 (트랜잭션이 끝날 때까지)
    // AI 정리 결과를 저장할 때 "메모가 그대로인지 확인 → 저장"을 한 번에 하려고 씀 (그 사이 다른 수정이 끼어들지 않게)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select n from VisitNote n
            where n.id = :id
              and n.visit.id = :visitId
              and n.deletedAt is null
            """)
    Optional<VisitNote> findMineForUpdate(@Param("id") Long id, @Param("visitId") Long visitId);

    // 일정을 지울 때 그 일정의 살아 있는 메모도 같이 지움 (soft delete)
    // 나중에 챗봇·리포트처럼 메모를 직접 읽는 기능에서 지운 일정의 메모가 섞이지 않게
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update VisitNote n set n.deletedAt = :now
            where n.visit.id = :visitId
              and n.deletedAt is null
            """)
    void deleteAllOfVisit(@Param("visitId") Long visitId, @Param("now") Instant now);
}
