package com.dangdang.domain.visit.repository;

import com.dangdang.domain.visit.entity.VisitQuestion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// 진료 질문 DB 저장·조회
public interface VisitQuestionRepository extends JpaRepository<VisitQuestion, Long> {

    // 이 일정의 살아 있는 질문 전부: 정렬 순서 → 먼저 만든 순
    List<VisitQuestion> findAllByVisitIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(Long visitId);

    // 이 일정에 붙은 이 질문 (지운 것도 찾음 → 다시 지우기 요청에 처음 지운 시각으로 답하려고)
    // 다른 일정의 질문 id로는 못 찾음
    // 찾으면서 이 질문 줄을 잠금 (트랜잭션이 끝날 때까지). 같은 질문 삭제가 동시에 두 번 와도
    // 나중 요청은 앞 요청이 끝난 뒤 "이미 지움"을 읽어서 처음 지운 시각을 덮어쓰지 않음
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from VisitQuestion q where q.id = :id and q.visit.id = :visitId")
    Optional<VisitQuestion> findByIdAndVisitIdForUpdate(@Param("id") Long id, @Param("visitId") Long visitId);

    // 이 일정의 살아 있는 질문 중 가장 큰 정렬 순서. 질문이 없으면 -1 (정렬 순서를 안 보내면 맨 뒤로 넣으려고)
    @Query("""
            select coalesce(max(q.sortOrder), -1) from VisitQuestion q
            where q.visit.id = :visitId
              and q.deletedAt is null
            """)
    int findMaxSortOrder(@Param("visitId") Long visitId);

    // 일정을 지울 때 그 일정의 살아 있는 질문도 같이 지움 (soft delete)
    // 나중에 챗봇·리포트처럼 질문을 직접 읽는 기능에서 지운 일정의 질문이 섞이지 않게
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update VisitQuestion q set q.deletedAt = :now
            where q.visit.id = :visitId
              and q.deletedAt is null
            """)
    void deleteAllOfVisit(@Param("visitId") Long visitId, @Param("now") Instant now);
}
