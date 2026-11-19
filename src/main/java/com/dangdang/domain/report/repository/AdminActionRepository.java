package com.dangdang.domain.report.repository;

import com.dangdang.domain.report.entity.AdminAction;
import com.dangdang.domain.report.enums.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminActionRepository extends JpaRepository<AdminAction, Long> {

    List<AdminAction> findByTargetTypeAndTargetIdOrderByOccurredAtDescIdDesc(ReportTargetType targetType, Long targetId);
}
