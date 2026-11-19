package com.dangdang.domain.report.repository;

import com.dangdang.domain.report.entity.Report;
import com.dangdang.domain.report.enums.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // 한 사람이 같은 글을 두 번 신고해도 한 줄
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO reports (reporter_id, target_type, target_id, reason, detail, status, created_at)
            VALUES (:reporterId, :targetType, :targetId, :reason, :detail, 'RECEIVED', :now)
            ON CONFLICT (reporter_id, target_type, target_id) DO NOTHING
            """)
    void addIfAbsent(@Param("reporterId") Long reporterId, @Param("targetType") String targetType,
                     @Param("targetId") Long targetId, @Param("reason") String reason,
                     @Param("detail") String detail, @Param("now") Instant now);

    // 신고 처리함 — 같은 대상의 신고를 한 줄로 묶고, 많이 쌓인 것·최근 것을 위로
    @Query(nativeQuery = true, value = """
            SELECT target_type AS targetType, target_id AS targetId, count(*) AS reportCount,
                   string_agg(DISTINCT reason, ',') AS reasons, max(created_at) AS lastReportedAt
            FROM reports
            WHERE status = :status
            GROUP BY target_type, target_id
            ORDER BY count(*) DESC, max(created_at) DESC, target_type, target_id DESC
            LIMIT 100
            """)
    List<ReportQueueRow> findQueue(@Param("status") String status);

    // 처리함 전체 묶음 수 (위 목록은 100줄까지만)
    @Query(nativeQuery = true, value = """
            SELECT count(*) FROM (
                SELECT 1 FROM reports WHERE status = :status GROUP BY target_type, target_id
            ) q
            """)
    long countQueue(@Param("status") String status);

    List<Report> findByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(ReportTargetType targetType, Long targetId);

    // 접수(RECEIVED)된 신고만 처리 상태로 바꾼다. 바뀐 줄 수를 돌려준다
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE reports SET status = :status, handled_at = :now
            WHERE target_type = :targetType AND target_id = :targetId AND status = 'RECEIVED'
            """)
    int markHandled(@Param("targetType") String targetType, @Param("targetId") Long targetId,
                    @Param("status") String status, @Param("now") Instant now);
}
