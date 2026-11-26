package com.dangdang.domain.admin.service;

import com.dangdang.domain.admin.entity.AdminLog;
import com.dangdang.domain.admin.enums.AdminLogAction;
import com.dangdang.domain.admin.repository.AdminLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

// 운영자 일을 처리 기록에 남긴다. 본래 일과 같은 트랜잭션에서 불러서, 일이 롤백되면 기록도 같이 없어진다
@Service
@RequiredArgsConstructor
public class AdminLogService {

    private final AdminLogRepository adminLogRepository;
    private final Clock clock;

    // MANDATORY: 트랜잭션 밖에서 부르면 바로 실패 — 일은 실패했는데 기록만 남는 경우를 막는다
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long adminId, AdminLogAction action, Long targetId, String targetLabel, String reason, String detail) {
        adminLogRepository.save(AdminLog.of(adminId, action, targetId, targetLabel, reason, detail, clock.instant()));
    }
}
