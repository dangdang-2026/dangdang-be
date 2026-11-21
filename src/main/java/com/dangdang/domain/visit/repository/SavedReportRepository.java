package com.dangdang.domain.visit.repository;

import com.dangdang.domain.visit.entity.SavedReport;
import com.dangdang.domain.visit.enums.ReportKind;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

// 저장한 리포트 DB 저장·조회
public interface SavedReportRepository extends JpaRepository<SavedReport, Long> {

    // 하나 찾기: 이 id + 내 것만 (아니면 Optional.empty)
    Optional<SavedReport> findByIdAndUserId(Long id, Long userId);

    // 지난 리포트 목록 한 페이지: 최근 저장 순. careSiteId를 주면 그 병원 것만
    // 진료용인데 그 진료 일정을 지웠으면 뺌 (상세에서도 404라서)
    // Slice라서 전체 개수(count)는 안 셈 → 다음 페이지가 있는지만 앎
    @Query("""
            select r from SavedReport r
            where r.user.id = :userId
              and (:careSiteId is null or r.careSiteId = :careSiteId)
              and (r.visitId is null
                   or exists (select 1 from Visit v where v.id = r.visitId and v.deletedAt is null))
            order by r.createdAt desc, r.id desc
            """)
    Slice<SavedReport> findPage(@Param("userId") Long userId, @Param("careSiteId") Long careSiteId, Pageable pageable);

    // 같은 kind·기간·진료 일정·병원으로 이미 저장한 리포트 (유니크 인덱스 uq_saved_reports_period와 같은 칸)
    // "= :visitId"는 null끼리 같다고 보지 않아서 null일 때를 따로 적음 (병원도 같음)
    @Query("""
            select r from SavedReport r
            where r.user.id = :userId
              and r.kind = :kind
              and r.fromDate = :from
              and r.toDate = :to
              and ((:visitId is null and r.visitId is null) or r.visitId = :visitId)
              and ((:careSiteId is null and r.careSiteId is null) or r.careSiteId = :careSiteId)
            """)
    Optional<SavedReport> findSame(@Param("userId") Long userId, @Param("kind") ReportKind kind,
                                   @Param("from") LocalDate from, @Param("to") LocalDate to,
                                   @Param("visitId") Long visitId, @Param("careSiteId") Long careSiteId);
}
