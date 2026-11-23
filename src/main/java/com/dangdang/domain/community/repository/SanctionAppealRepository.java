package com.dangdang.domain.community.repository;

import com.dangdang.domain.community.entity.SanctionAppeal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SanctionAppealRepository extends JpaRepository<SanctionAppeal, Long> {

    boolean existsBySanctionId(Long sanctionId);

    // 운영자 목록 — 최신 먼저, 제한 줄도 같이 읽는다
    @Query("select a from SanctionAppeal a join fetch a.sanction order by a.createdAt desc, a.id desc")
    List<SanctionAppeal> findAllLatest();
}
