package com.dangdang.domain.reading.repository;

import com.dangdang.domain.reading.entity.Reading;
import org.springframework.data.jpa.repository.JpaRepository;

// 혈당 기록 DB 저장·조회 (기본 기능은 JpaRepository가 자동으로 만들어 줌)
public interface ReadingRepository extends JpaRepository<Reading, Long> {
}
