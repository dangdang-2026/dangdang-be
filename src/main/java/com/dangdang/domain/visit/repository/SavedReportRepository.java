package com.dangdang.domain.visit.repository;

import com.dangdang.domain.visit.entity.SavedReport;
import com.dangdang.domain.visit.enums.ReportKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

// 저장한 리포트 DB 저장·조회
public interface SavedReportRepository extends JpaRepository<SavedReport, Long> {

    // 하나 찾기: 이 id + 내 것만 (아니면 Optional.empty)
    Optional<SavedReport> findByIdAndUserId(Long id, Long userId);

    // 이 진료 일정으로 이미 저장한 진료용 리포트 (유니크 인덱스 uq_saved_reports_visit와 같은 칸)
    Optional<SavedReport> findByUserIdAndKindAndVisitId(Long userId, ReportKind kind, Long visitId);

    // 같은 기간·병원으로 이미 저장한 기간 직접 리포트 (유니크 인덱스 uq_saved_reports_custom과 같은 칸)
    // "= :careSiteId"는 null끼리 같다고 보지 않아서 null일 때를 따로 적음
    @Query("""
            select r from SavedReport r
            where r.user.id = :userId
              and r.kind = com.dangdang.domain.visit.enums.ReportKind.CUSTOM
              and r.fromDate = :from
              and r.toDate = :to
              and ((:careSiteId is null and r.careSiteId is null) or r.careSiteId = :careSiteId)
            """)
    Optional<SavedReport> findSameCustom(@Param("userId") Long userId, @Param("from") LocalDate from,
                                         @Param("to") LocalDate to, @Param("careSiteId") Long careSiteId);

    // 지난 리포트 목록 한 페이지: 최근 저장 순
    // careSiteId를 주면 그 병원 것만 (진료용은 지금 진료 일정의 병원, 기간 직접은 저장한 병원)
    // until을 주면 그날까지의 리포트만 (임신 중 기록 보관함 E-10b: until = 출산일)
    //   진료용은 진료 날짜(한국)가 until 이하 → visitAt < untilEnd(until 다음 날 0시), 날짜 미정 진료는 뺌
    //   기간 직접은 끝 날짜(to_date)가 until 이하. 진료용 기간은 저장하지 않아서 진료 날짜로 거름
    // 진료용인데 그 진료 일정을 지웠으면 뺌 (상세에서도 404라서)
    // Page라서 전체 개수도 셈 (보관함 「리포트 6장」·쪽 번호). count는 같은 조건으로 따로 (order by 없이)
    @Query(value = """
            select r from SavedReport r
            where r.user.id = :userId
              and (r.visitId is null
                   or exists (select 1 from Visit v where v.id = r.visitId and v.deletedAt is null))
              and (:careSiteId is null
                   or (r.visitId is null and r.careSiteId = :careSiteId)
                   or exists (select 1 from Visit v2 where v2.id = r.visitId and v2.careSiteId = :careSiteId))
              and (cast(:until as LocalDate) is null
                   or (r.visitId is null and r.toDate <= :until)
                   or exists (select 1 from Visit v3 where v3.id = r.visitId
                              and v3.visitAt is not null and v3.visitAt < :untilEnd))
            order by r.createdAt desc, r.id desc
            """,
            countQuery = """
            select count(r) from SavedReport r
            where r.user.id = :userId
              and (r.visitId is null
                   or exists (select 1 from Visit v where v.id = r.visitId and v.deletedAt is null))
              and (:careSiteId is null
                   or (r.visitId is null and r.careSiteId = :careSiteId)
                   or exists (select 1 from Visit v2 where v2.id = r.visitId and v2.careSiteId = :careSiteId))
              and (cast(:until as LocalDate) is null
                   or (r.visitId is null and r.toDate <= :until)
                   or exists (select 1 from Visit v3 where v3.id = r.visitId
                              and v3.visitAt is not null and v3.visitAt < :untilEnd))
            """)
    Page<SavedReport> findPage(@Param("userId") Long userId, @Param("careSiteId") Long careSiteId,
                               @Param("until") LocalDate until, @Param("untilEnd") Instant untilEnd,
                               Pageable pageable);
}
