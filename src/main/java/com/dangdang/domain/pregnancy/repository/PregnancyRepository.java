package com.dangdang.domain.pregnancy.repository;

import com.dangdang.domain.pregnancy.entity.Pregnancy;
import com.dangdang.domain.pregnancy.enums.PregnancyState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PregnancyRepository extends JpaRepository<Pregnancy, Long> {

    // 진행 중인 임신: state가 ENDED가 아닌 줄
    Optional<Pregnancy> findByUserIdAndStateNot(Long userId, PregnancyState state);
}
