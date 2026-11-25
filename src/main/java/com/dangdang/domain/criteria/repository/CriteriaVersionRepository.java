package com.dangdang.domain.criteria.repository;

import com.dangdang.domain.criteria.entity.CriteriaVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CriteriaVersionRepository extends JpaRepository<CriteriaVersion, Long> {

    // 이력: 이 사람 것 전부, 적용일 늦은 순 → id 큰 순
    List<CriteriaVersion> findAllByUserIdOrderByEffectiveFromDescIdDesc(Long userId);

    // 그 날짜 기준: 적용일 <= 그 날짜 인 것 중 첫 번째 하나 (없으면 Optional.empty)
    // 서버가 만든 빈 기준(autoEmpty)은 진짜 기준이 하나도 없을 때만 고름 → 지난 날짜 기준이 덮은 빈 기준이 남아 있어도 새 기준을 가리지 않게
    Optional<CriteriaVersion> findFirstByUserIdAndEffectiveFromLessThanEqualOrderByAutoEmptyAscEffectiveFromDescIdDesc(
            Long userId, LocalDate date);

    // 이 날짜와 그 뒤에 시작하는 기준 (지난 날짜 기준을 적을 때 그 기준이 어디까지 쓰이는지·덮이는 빈 기준 찾기). 적용일 이른 순 → id 작은 순
    List<CriteriaVersion> findAllByUserIdAndEffectiveFromGreaterThanEqualOrderByEffectiveFromAscIdAsc(
            Long userId, LocalDate date);

    // 이 중 어떤 혈당도 가리키지 않는 줄만 지움 (지운 혈당 포함 — readings.criteria_version_id 외래키에 안 걸리게). 지운 줄 수
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from CriteriaVersion c
            where c.user.id = :userId
              and c.id in :ids
              and not exists (select 1 from Reading r where r.criteriaVersion = c)
            """)
    int deleteUnused(Long userId, Collection<Long> ids);
}
