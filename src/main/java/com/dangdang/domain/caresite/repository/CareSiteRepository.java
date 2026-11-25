package com.dangdang.domain.caresite.repository;

import com.dangdang.domain.caresite.entity.CareSite;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// 병원 DB 저장·조회
public interface CareSiteRepository extends JpaRepository<CareSite, Long> {

    // 하나 찾기: 이 id + 내 것 + 삭제 안 된 것만 (아니면 Optional.empty)
    Optional<CareSite> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    // 병원 지우기 — 내 병원(삭제 안 된 것) 줄을 FOR UPDATE로 잠그고 읽는다
    // 일정 등록·수정이 같은 병원 줄을 FOR SHARE로 잡으니, 지우는 동안 그 병원에 새 다가오는 일정이 끼어들지 못한다
    // 잠금 순서는 늘 care_sites → visits (일정 등록·수정·병원 지우기 모두)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CareSite c where c.id = :id and c.user.id = :userId and c.deletedAt is null")
    Optional<CareSite> findMineForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    // 일정 등록·수정 — 고른 병원 줄을 FOR SHARE로 잠그고 읽는다 (지운 병원도 읽음 → 부르는 쪽이 deletedAt을 본다)
    // 병원 지우기가 먼저 잡았으면 끝날 때까지 기다렸다가 지워진 상태를 본다
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from CareSite c where c.id = :id and c.user.id = :userId")
    Optional<CareSite> findMineForShare(@Param("id") Long id, @Param("userId") Long userId);

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
