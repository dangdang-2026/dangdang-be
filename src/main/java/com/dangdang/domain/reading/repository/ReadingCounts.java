package com.dangdang.domain.reading.repository;

// 기간 안 혈당 개수만 DB에서 센 결과 (지난 리포트 목록 카드 숫자용)
// 목표 비교는 ReadingEvaluator.aboveTarget과 같은 규칙: 공복·식후만, 그때 기준에 목표가 있을 때만, 목표값 이상이면 밖
public record ReadingCounts(
        long measured,               // 측정
        long judged,                 // 그때 목표와 비교한 기록
        long outOfTarget) {          // 그중 목표 밖
}
