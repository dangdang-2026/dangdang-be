package com.dangdang.domain.caresite.repository;

import com.dangdang.domain.caresite.entity.CareSite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// 병원 DB 저장·조회
public interface CareSiteRepository extends JpaRepository<CareSite, Long> {

    // 하나 찾기: 이 id + 내 것 + 삭제 안 된 것만 (아니면 Optional.empty)
    Optional<CareSite> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    // 지운 병원도 포함해서 내 병원 하나 (식사 안내 「받은 곳」 이름 — C-20 「그대로 남아요」)
    // 일부러 deleted_at을 거르지 않는다. 남의 병원은 userId 조건으로 막힌다
    Optional<CareSite> findByIdAndUserId(Long id, Long userId);

    // 내 병원 전부: 대표 병원 먼저 → 정렬 순서 → 먼저 등록한 순
    List<CareSite> findAllByUserIdAndDeletedAtIsNullOrderByPrimaryDescSortOrderAscIdAsc(Long userId);

    // 내 병원 전부 (지운 것 포함) — 진료 메모를 AI로 보내기 전 병원 이름 가리기용. 예전 메모에 지운 병원 이름이 남아 있을 수 있어서
    List<CareSite> findAllByUserId(Long userId);

    // 지금 대표 병원 (없을 수 있음)
    Optional<CareSite> findByUserIdAndPrimaryTrueAndDeletedAtIsNull(Long userId);
}
