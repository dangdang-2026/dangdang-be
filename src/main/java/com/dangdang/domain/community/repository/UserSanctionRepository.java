package com.dangdang.domain.community.repository;

import com.dangdang.domain.community.entity.UserSanction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface UserSanctionRepository extends JpaRepository<UserSanction, Long> {

    // 지금 걸려 있는 제한 — 끝나는 날이 가장 늦은 것이 먼저 (기간 없음이 맨 앞)
    @Query("select s from UserSanction s where s.user.id = :userId and s.liftedAt is null"
            + " and s.startsAt <= :now and (s.endsAt is null or s.endsAt > :now)"
            + " order by case when s.endsAt is null then 0 else 1 end, s.endsAt desc")
    List<UserSanction> findActive(Long userId, Instant now);
}
