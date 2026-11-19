package com.dangdang.domain.report.entity;

import com.dangdang.domain.report.enums.AdminActionType;
import com.dangdang.domain.report.enums.ReportReason;
import com.dangdang.domain.report.enums.ReportTargetType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "admin_actions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long adminId;                    // 운영자 계정이 지워져도 번호는 남는다

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AdminActionType action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReportTargetType targetType;

    @Column(nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ReportReason reason;             // HIDE일 때만

    @Column(length = 200)
    private String memo;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    public static AdminAction of(Long adminId, AdminActionType action, ReportTargetType targetType, Long targetId,
                                 ReportReason reason, String memo, Instant now) {
        AdminAction a = new AdminAction();
        a.adminId = adminId;
        a.action = action;
        a.targetType = targetType;
        a.targetId = targetId;
        a.reason = reason;
        a.memo = memo;
        a.occurredAt = now;
        return a;
    }
}
