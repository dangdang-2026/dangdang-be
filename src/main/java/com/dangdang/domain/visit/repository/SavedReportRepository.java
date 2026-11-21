package com.dangdang.domain.visit.repository;

import com.dangdang.domain.visit.entity.SavedReport;
import com.dangdang.domain.visit.enums.ReportKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

// 저장한 리포트 DB 저장·조회
public interface SavedReportRepository extends JpaRepository<SavedReport, Long> {

    // 하나 찾기: 이 id + 내 것만 (아니면 Optional.empty)
    Optional<SavedReport> findByIdAndUserId(Long id, Long userId);

    // 같은 kind·기간·진료 일정으로 이미 저장한 리포트 (visitId가 null이면 visit_id도 null인 것)
    // "= :visitId"는 null끼리 같다고 보지 않아서 null일 때를 따로 적음
    @Query("""
            select r from SavedReport r
            where r.user.id = :userId
              and r.kind = :kind
              and r.fromDate = :from
              and r.toDate = :to
              and ((:visitId is null and r.visitId is null) or r.visitId = :visitId)
            """)
    Optional<SavedReport> findSame(@Param("userId") Long userId, @Param("kind") ReportKind kind,
                                   @Param("from") LocalDate from, @Param("to") LocalDate to,
                                   @Param("visitId") Long visitId);
}
