package com.dangdang.domain.report.entity;

import com.dangdang.domain.community.entity.CommunityProfile;
import com.dangdang.domain.report.enums.ReportReason;
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

    @Column(nullable = false, length = 10)
    private String status;            // RECEIVED / HIDDEN / DISMISSED (처리는 6주차 운영자 화면)

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant handledAt;
}
