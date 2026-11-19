package com.dangdang.domain.report.repository;

import java.time.Instant;

// 신고 처리함 한 줄 = 대상(글·댓글) 하나에 쌓인 신고 묶음
public interface ReportQueueRow {
    String getTargetType();

    Long getTargetId();

    long getReportCount();

    String getReasons();          // 사유 이름을 쉼표로 이은 것 (예: "ABUSE,MEDICAL_ADVICE")

    Instant getLastReportedAt();
}
