package com.dangdang.domain.visit.dto.request;

import java.time.Instant;

// 진료 일정 등록 요청. 둘 다 없어도 됨 (병원·날짜를 아직 모르는 일정)
public record VisitCreateRequest(
        Long careSiteId,             // 내 병원 id (병원 API에서 받은 careSiteId)
        Instant visitAt) {           // 진료 날짜·시각. JSON은 "2026-12-03T10:30:00+09:00" 처럼 시간대 포함. 지난 날짜도 됨
}
