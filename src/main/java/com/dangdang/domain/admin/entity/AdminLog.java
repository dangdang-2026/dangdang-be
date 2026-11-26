package com.dangdang.domain.admin.entity;

import com.dangdang.domain.admin.enums.AdminLogAction;
import com.dangdang.domain.admin.enums.AdminLogTarget;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// 운영자 처리 기록 한 줄. 남기기만 하고 고치지 않는다 (setter 없음)
@Getter
@Entity
@Table(name = "admin_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private AdminLogAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private AdminLogTarget targetType;

    @Column(nullable = false, updatable = false)
    private Long targetId;

    @Column(length = 100, updatable = false)
    private String targetLabel;

    @Column(length = 50, updatable = false)
    private String reason;

    @Column(length = 200, updatable = false)
    private String detail;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    public static AdminLog of(Long adminId, AdminLogAction action, Long targetId, String targetLabel,
                              String reason, String detail, Instant now) {
        AdminLog log = new AdminLog();
        log.adminId = adminId;
        log.action = action;
        log.targetType = action.getTarget();
        log.targetId = targetId;
        log.targetLabel = cut(targetLabel, 100);
        log.reason = cut(reason, 50);
        log.detail = cut(detail, 200);
        log.occurredAt = now;
        return log;
    }

    // 칸보다 긴 제목이 와도 기록 때문에 본래 일이 실패하지 않게 자른다
    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
