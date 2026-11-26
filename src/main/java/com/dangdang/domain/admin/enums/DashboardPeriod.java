package com.dangdang.domain.admin.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 운영자 대시보드 기간 탭 (오늘 · 7일 · 30일). 오늘을 포함해 며칠을 볼지
@Getter
@RequiredArgsConstructor
public enum DashboardPeriod {
    TODAY(1),
    WEEK(7),
    MONTH(30);

    private final int days;
}
