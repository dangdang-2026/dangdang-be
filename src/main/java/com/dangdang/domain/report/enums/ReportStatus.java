package com.dangdang.domain.report.enums;

// 신고 한 건의 상태
public enum ReportStatus {
    RECEIVED,   // 접수 — 신고 처리함에 보인다
    HIDDEN,     // 운영자가 대상을 가림
    DISMISSED   // 운영자가 「문제없음」
}
