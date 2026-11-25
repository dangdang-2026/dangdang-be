package com.dangdang.domain.record.enums;

// 달력 날짜 하나의 상태 (빨간 표시·판정 없이 기록 여부만)
public enum CalendarDayStatus {
    RECORDED,       // 혈당·식사·인슐린 기록이 하나라도 있는 날
    EMPTY,          // 기록 없는 날
    TODAY,          // 오늘
    FUTURE,         // 오늘 이후
    BEFORE_SIGNUP   // 가입하기 전 날 (흐리게)
}
