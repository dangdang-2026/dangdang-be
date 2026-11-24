package com.dangdang.domain.criteria.repository;

import com.dangdang.domain.criteria.entity.CriteriaVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CriteriaVersionRepository extends JpaRepository<CriteriaVersion, Long> {

    // 이력: 이 사람 것 전부, 적용일 늦은 순 → id 큰 순
    List<CriteriaVersion> findAllByUserIdOrderByEffectiveFromDescIdDesc(Long userId);

    // 지금 기준: 적용일 <= 오늘 인 것 중 첫 번째 하나 (없으면 Optional.empty)
    Optional<CriteriaVersion> findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
            Long userId, LocalDate today);

    // 이 날짜보다 뒤에 시작하는 첫 기준 (지난 날짜 기준을 적을 때 그 기준이 어디까지 쓰이는지). 적용일 이른 순 → id 작은 순
    Optional<CriteriaVersion> findFirstByUserIdAndEffectiveFromGreaterThanOrderByEffectiveFromAscIdAsc(
            Long userId, LocalDate date);
}
