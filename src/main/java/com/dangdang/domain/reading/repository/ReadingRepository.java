package com.dangdang.domain.reading.repository;

import com.dangdang.domain.reading.entity.Reading;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// 혈당 기록 DB 저장·조회 (기본 기능은 JpaRepository가 자동으로 만들어 줌)
public interface ReadingRepository extends JpaRepository<Reading, Long> {

    // 하나 찾기: 이 id + 내 것 + 삭제 안 된 것만 (아니면 Optional.empty)
    Optional<Reading> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
}
