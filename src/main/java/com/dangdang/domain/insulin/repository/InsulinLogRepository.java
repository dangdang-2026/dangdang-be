package com.dangdang.domain.insulin.repository;

import com.dangdang.domain.insulin.entity.InsulinLog;
import org.springframework.data.jpa.repository.JpaRepository;

// 인슐린 기록 DB 저장·조회
public interface InsulinLogRepository extends JpaRepository<InsulinLog, Long> {
}
