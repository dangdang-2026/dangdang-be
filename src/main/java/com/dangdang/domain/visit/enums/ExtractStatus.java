package com.dangdang.domain.visit.enums;

// AI 정리 결과 한 칸의 확신 정도 (AI 서버가 정함)
public enum ExtractStatus {
    FOUND,          // 근거 문장이 메모에 그대로 있고 숫자도 그 문장에 있음 → 화면에서 체크된 채로
    CHECK_NEEDED    // 애매함 (상대 날짜, "~였나", 범위 밖, 숫자가 글자로) → 「확인 필요」 + 체크 해제
}
