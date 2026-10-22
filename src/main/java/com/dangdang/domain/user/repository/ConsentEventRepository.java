package com.dangdang.domain.user.repository;

import com.dangdang.domain.user.entity.ConsentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentEventRepository extends JpaRepository<ConsentEvent, Long> {
}
