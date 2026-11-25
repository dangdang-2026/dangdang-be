package com.dangdang.domain.report.repository;

import com.dangdang.domain.report.entity.Report;
import com.dangdang.domain.report.enums.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
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

    // U-15b · U-30: 올리기 전 안내를 보고도 올린 글 → 신고한 사람 없이(시스템) 처리함에 넣는다
    // reporter_id NULL끼리는 UNIQUE에 안 걸린다. 같은 글·사유가 두 번 들어가지 않게 부르는 쪽(PostService)이 사유를 한 번씩만 넘긴다
    // (방금 만든 글이라 다른 요청이 같은 글에 시스템 신고를 넣을 수 없다)
    // reporter_id를 파라미터 null로 넘기지 않고 SQL에 NULL로 적는다 (Hibernate가 null 파라미터 타입을 못 정하면 Postgres가 bytea 오류)
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO reports (reporter_id, target_type, target_id, reason, detail, status, created_at)
            VALUES (NULL, 'POST', :postId, :reason, :detail, 'RECEIVED', :now)
            """)
    void addSystemReport(@Param("postId") Long postId, @Param("reason") String reason,
                         @Param("detail") String detail, @Param("now") Instant now);

    // 신고 처리함 — 같은 대상의 신고를 한 줄로 묶고, 위급(CRISIS) 신고가 하나라도 있으면 신고 수와 상관없이 맨 위 (U-13b)
    // 그다음은 많이 쌓인 것·최근 것을 위로, 마지막은 대상 번호로 순서를 고정한다
    // totalCount = 전체 묶음 수 (LIMIT 전에 세서 100을 넘을 수 있다). 목록과 같은 쿼리라 두 숫자가 어긋나지 않는다
    @Query(nativeQuery = true, value = """
            SELECT target_type AS targetType, target_id AS targetId, count(*) AS reportCount,
                   string_agg(DISTINCT reason, ',' ORDER BY reason) AS reasons, max(created_at) AS lastReportedAt,
                   count(*) OVER () AS totalCount
            FROM reports
            WHERE status = :status
            GROUP BY target_type, target_id
            ORDER BY bool_or(reason = 'CRISIS') DESC, count(*) DESC, max(created_at) DESC, target_type, target_id DESC
            LIMIT 100
            """)
    List<ReportQueueRow> findQueue(@Param("status") String status);

    List<Report> findByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(ReportTargetType targetType, Long targetId);

    // 이 대상의 신고 중 from 상태인 것만 to 상태로 바꾼다. 바뀐 줄 수를 돌려준다
    // (가리기: 접수·문제없음 → 가림 / 문제없음: 접수 → 문제없음 / 되돌리기: 가림 → 문제없음)
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE reports SET status = :to, handled_at = :now
            WHERE target_type = :targetType AND target_id = :targetId AND status IN (:from)
            """)
    int moveStatus(@Param("targetType") String targetType, @Param("targetId") Long targetId,
                   @Param("from") Collection<String> from, @Param("to") String to, @Param("now") Instant now);
}
