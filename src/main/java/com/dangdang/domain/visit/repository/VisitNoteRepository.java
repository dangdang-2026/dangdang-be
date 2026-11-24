package com.dangdang.domain.visit.repository;

import com.dangdang.domain.visit.entity.VisitNote;
import com.dangdang.domain.visit.entity.VisitNoteExtract;
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

// 진료 메모 DB 저장·조회
public interface VisitNoteRepository extends JpaRepository<VisitNote, Long> {

    // 이 일정의 살아 있는 메모 전부: 먼저 쓴 순
    List<VisitNote> findAllByVisitIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(Long visitId);

    // 이 일정에 붙은 이 메모 + 삭제 안 된 것만 (다른 일정의 메모 id로는 못 찾음)
    Optional<VisitNote> findByIdAndVisitIdAndDeletedAtIsNull(Long id, Long visitId);

    // 위와 같은 조건으로 찾으면서 이 메모 줄을 잠금 (트랜잭션이 끝날 때까지)
    // AI 정리 결과를 저장할 때 "메모가 그대로인지 확인 → 저장"을 한 번에 하려고 씀 (그 사이 다른 수정이 끼어들지 않게)
    // 메모 수정도 이걸로 읽음. 안 잠그고 읽으면 수정이 저장될 때 그사이 들어온 정리 결과를 옛 값으로 덮어씀
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select n from VisitNote n
            where n.id = :id
              and n.visit.id = :visitId
              and n.deletedAt is null
            """)
    Optional<VisitNote> findMineForUpdate(@Param("id") Long id, @Param("visitId") Long visitId);

    // AI 정리 결과만 저장 (updated_at은 그대로 → 사용자가 고친 것처럼 보이지 않게). 다시 정리하면 새 결과로 바뀜
    // findMineForUpdate로 줄을 잠근 같은 트랜잭션 안에서 부름
    @Modifying
    @Query("update VisitNote n set n.aiExtract = :extract where n.id = :id")
    void saveExtract(@Param("id") Long id, @Param("extract") VisitNoteExtract extract);

    // 일정을 지울 때 그 일정의 살아 있는 메모도 같이 지움 (soft delete)
    // 나중에 챗봇·리포트처럼 메모를 직접 읽는 기능에서 지운 일정의 메모가 섞이지 않게
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update VisitNote n set n.deletedAt = :now
            where n.visit.id = :visitId
              and n.deletedAt is null
            """)
    void deleteAllOfVisit(@Param("visitId") Long visitId, @Param("now") Instant now);

    // 여러 일정의 살아 있는 메모를 한 번에 지움 (병원을 지울 때 다가오는 일정과 같이)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update VisitNote n set n.deletedAt = :now
            where n.visit.id in :visitIds
              and n.deletedAt is null
            """)
    void deleteAllOfVisits(@Param("visitIds") Collection<Long> visitIds, @Param("now") Instant now);
}
