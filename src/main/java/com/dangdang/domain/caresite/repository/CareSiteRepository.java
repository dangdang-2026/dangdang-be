package com.dangdang.domain.caresite.repository;

import com.dangdang.domain.caresite.entity.CareSite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// 병원 DB 저장·조회
public interface CareSiteRepository extends JpaRepository<CareSite, Long> {

    // 하나 찾기: 이 id + 내 것 + 삭제 안 된 것만 (아니면 Optional.empty)
    Optional<CareSite> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    // 내 병원 전부: 대표 병원 먼저 → 정렬 순서 → 먼저 등록한 순
    List<CareSite> findAllByUserIdAndDeletedAtIsNullOrderByPrimaryDescSortOrderAscIdAsc(Long userId);

    // 지금 대표 병원 (없을 수 있음)
    Optional<CareSite> findByUserIdAndPrimaryTrueAndDeletedAtIsNull(Long userId);
}
