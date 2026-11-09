package com.dangdang.domain.reading.repository;

import com.dangdang.domain.reading.entity.Reading;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// 혈당 기록 DB 저장·조회 (기본 기능은 JpaRepository가 자동으로 만들어 줌)
public interface ReadingRepository extends JpaRepository<Reading, Long> {

    // 하나 찾기: 이 id + 내 것 + 삭제 안 된 것만 (아니면 Optional.empty)
    Optional<Reading> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    // 목록 한 페이지: 내 것 + 삭제 안 됨 + from 이후 + 커서(cursorAt, cursorId)보다 과거. 최신순
    // 첫 페이지는 cursorAt = 기간 끝(to 다음 날 0시), cursorId = 0 으로 부름 (null 안 씀. 0이라 기간 끝 시각과 딱 같은 기록은 빠짐)
    @Query("""
            select r from Reading r
            where r.user.id = :userId
              and r.deletedAt is null
              and r.measuredAt >= :from
              and (r.measuredAt < :cursorAt
                   or (r.measuredAt = :cursorAt and r.id < :cursorId))
            order by r.measuredAt desc, r.id desc
            """)
    List<Reading> findPage(Long userId, Instant from, Instant cursorAt, Long cursorId, Limit limit);
}
