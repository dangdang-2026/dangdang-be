package com.dangdang.domain.report.repository;

import com.dangdang.domain.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

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
}
