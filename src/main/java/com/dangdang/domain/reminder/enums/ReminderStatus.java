package com.dangdang.domain.reminder.enums;

//측정 알림 상태
public enum ReminderStatus {
    SCHEDULED,      //예약됨
    SENT,           //보냄
    OPENED,         //눌러서 열어 봄
    DONE,           //혈당 저장 끝
    SKIPPED,        //안 보내고 건너뜀 (설정 꺼짐 등, 서버가)
    DISMISSED,      //사용자가 「이번 끼니는 건너뛸게요」 (H-05) — 놓친 측정에 셈
    PAUSED,         //잠시 멈춤
    CANCELED        //취소
}
