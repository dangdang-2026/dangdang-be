package com.dangdang.domain.visit.infra;

import com.dangdang.domain.visit.entity.VisitNoteExtract;

import java.time.LocalDate;

// 진료 메모 정리를 AI 서버(POST /internal/visit-note/organize)에 맡기는 창구 (테스트에서는 가짜로 바꾼다)
public interface VisitNoteOrganizeClient {

    // memo: 메모 원문 (1~3000자), visitDate: 진료일 ("2주 뒤 금요일"을 날짜로 바꿀 때 기준)
    // 실패하면 RestClientException이 그대로 나감 → 서비스가 받아서 「정리하지 못했어요」
    VisitNoteExtract organize(String requestId, String memo, LocalDate visitDate);
}
