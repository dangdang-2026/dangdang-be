package com.dangdang.domain.insulin.repository;

import com.dangdang.domain.insulin.entity.InsulinLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

// 인슐린 기록 DB 저장·조회
public interface InsulinLogRepository extends JpaRepository<InsulinLog, Long> {

    // 하나 찾기: 이 id + 내 것 + 삭제 안 된 것만 (아니면 Optional.empty)
    Optional<InsulinLog> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    // 기간 안 기록 한 페이지: 내 것 + 삭제 안 됨 + from 이상 to 미만, 맞은 시각 오래된 순 (같으면 먼저 적은 순)
    // Slice라서 전체 개수(count)는 안 셈 → 다음 페이지가 있는지만 앎
    @Query("""
            select l from InsulinLog l
            where l.user.id = :userId
              and l.deletedAt is null
              and l.takenAt >= :from
              and l.takenAt < :to
            order by l.takenAt asc, l.id asc
            """)
    Slice<InsulinLog> findPage(@Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to,
                               Pageable pageable);
}
