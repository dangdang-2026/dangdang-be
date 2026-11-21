package com.dangdang.domain.pregnancy.repository;

import com.dangdang.domain.pregnancy.entity.Pregnancy;
import com.dangdang.domain.pregnancy.enums.PregnancyState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PregnancyRepository extends JpaRepository<Pregnancy, Long> {

    // 진행 중인 임신: state가 ENDED가 아닌 줄
    Optional<Pregnancy> findByUserIdAndStateNot(Long userId, PregnancyState state);

    // 이 사람 임신 전부 (끝난 임신 포함), 최근 것 먼저. 리포트 기간과 겹치는 임신을 고를 때
    List<Pregnancy> findAllByUserIdOrderByIdDesc(Long userId);

    // 끝난 임신이 있는지 (출산 뒤 졸업한 사람은 측정 알림을 보내지 않는다)
    boolean existsByUserIdAndState(Long userId, PregnancyState state);

    // 졸업 화면 「지킨 측정」 — 지우지 않은 혈당 기록 수
    @Query("select count(r) from Reading r where r.user.id = :userId and r.deletedAt is null")
    long countReadings(Long userId);
}
