package com.dangdang.domain.report.repository;

import java.time.Instant;

// 신고 처리함 한 줄 = 대상(글·댓글) 하나에 쌓인 신고 묶음
public interface ReportQueueRow {
    String getTargetType();

    Long getTargetId();

    long getReportCount();

    String getReasons();          // 사유 이름을 가나다순으로 쉼표로 이은 것 (예: "ABUSE,MEDICAL_ADVICE")

    Instant getLastReportedAt();

    long getTotalCount();         // 전체 묶음 수 (줄마다 같은 값)
}
