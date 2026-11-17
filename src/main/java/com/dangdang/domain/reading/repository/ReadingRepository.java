package com.dangdang.domain.reading.repository;

import com.dangdang.domain.reading.entity.Reading;
import com.dangdang.domain.reading.enums.ReadingType;
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

    // 기간 안 기록 전부 (오늘 화면·기록 통합 조회용). from 이상 to 미만, 잰 시각 오래된 순
    // 병원 기준도 같이 가져옴 (목표 비교할 때 기록마다 기준을 또 조회하지 않게)
    @Query("""
            select r from Reading r
            join fetch r.criteriaVersion
            where r.user.id = :userId
              and r.deletedAt is null
              and r.measuredAt >= :from
              and r.measuredAt < :to
            order by r.measuredAt asc, r.id asc
            """)
    List<Reading> findAllInRange(Long userId, Instant from, Instant to);

    // 기간 안 기록 중 이 종류(공복·식후)만 (통계용). 병원 기준도 같이 가져옴 (목표 안/밖 계산)
    @Query("""
            select r from Reading r
            join fetch r.criteriaVersion
            where r.user.id = :userId
              and r.deletedAt is null
              and r.readingType = :readingType
              and r.measuredAt >= :from
              and r.measuredAt < :to
            order by r.measuredAt asc, r.id asc
            """)
    List<Reading> findAllInRangeByType(Long userId, ReadingType readingType, Instant from, Instant to);

    // 기간 안 잰 시각만 (달력에서 기록 있는 날 찾기용)
    @Query("""
            select r.measuredAt from Reading r
            where r.user.id = :userId
              and r.deletedAt is null
              and r.measuredAt >= :from
              and r.measuredAt < :to
            """)
    List<Instant> findMeasuredAtsInRange(Long userId, Instant from, Instant to);

    // 가장 처음 잰 시각 (기록이 하나도 없으면 null)
    @Query("""
            select min(r.measuredAt) from Reading r
            where r.user.id = :userId
              and r.deletedAt is null
            """)
    Instant findFirstMeasuredAt(Long userId);
}
