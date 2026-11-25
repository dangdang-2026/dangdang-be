package com.dangdang.domain.report.entity;

import com.dangdang.domain.community.entity.CommunityProfile;
import com.dangdang.domain.report.enums.ReportReason;
import com.dangdang.domain.report.enums.ReportSource;
import com.dangdang.domain.report.enums.ReportStatus;
import com.dangdang.domain.report.enums.ReportTargetType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "reports")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    private CommunityProfile reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReportTargetType targetType;

    @Column(nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportReason reason;

    @Column(length = 200)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReportSource source;      // 넣는 건 ReportRepository의 native INSERT만 (USER는 DB 기본값)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReportStatus status;      // 바꾸는 건 운영자 처리함(ReportRepository.moveStatus)에서만

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant handledAt;
}
