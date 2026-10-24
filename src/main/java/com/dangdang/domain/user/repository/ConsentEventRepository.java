package com.dangdang.domain.user.repository;

import com.dangdang.domain.user.entity.ConsentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsentEventRepository extends JpaRepository<ConsentEvent, Long> {
    // 최근 것부터. 같은 시각이면 나중에 들어간 줄(id 큰 것)이 먼저
    List<ConsentEvent> findByUser_IdOrderByOccurredAtDescIdDesc(Long userId);
}
