package com.dangdang.domain.report.enums;

// 신고가 어디서 왔나
public enum ReportSource {
    USER,     // 사용자가 직접 신고 (신고자가 탈퇴하면 reporter_id만 NULL이 되고 USER 그대로)
    SYSTEM    // 서버 자동 신고: 올리기 전 안내를 보고도 올린 글 (U-15b · U-30). 신고자 없음
}
