package com.dangdang.domain.community.repository;

import com.dangdang.domain.community.entity.UserSanction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserSanctionRepository extends JpaRepository<UserSanction, Long> {

    // 지금 걸려 있는 제한 — 끝나는 날이 가장 늦은 것이 먼저 (기간 없음이 맨 앞)
    @Query("select s from UserSanction s where s.user.id = :userId and s.liftedAt is null"
            + " and s.startsAt <= :now and (s.endsAt is null or s.endsAt > :now)"
            + " order by case when s.endsAt is null then 0 else 1 end, s.endsAt desc")
    List<UserSanction> findActive(@Param("userId") Long userId, @Param("now") Instant now);

    // 풀기 — 두 운영자가 동시에 눌러도 한 번만 풀리고 기록도 한 줄만 남게 (PR #165 리뷰)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserSanction s where s.id = :id")
    Optional<UserSanction> findByIdForUpdate(@Param("id") Long id);
}
