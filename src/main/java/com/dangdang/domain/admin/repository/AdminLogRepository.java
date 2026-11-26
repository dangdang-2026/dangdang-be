package com.dangdang.domain.admin.repository;

import com.dangdang.domain.admin.entity.AdminLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminLogRepository extends JpaRepository<AdminLog, Long> {
}
