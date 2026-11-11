package com.dangdang.domain.notification.enums;

//알림함 종류 (notifications.type 칸, VARCHAR(20))
public enum NotificationType {
    MEASURE,            //혈당 측정
    VISIT_BEFORE,       //진료 전날
    VISIT_TODAY,        //진료 당일
    VISIT_NOTE,         //진료 후 메모
    POSTPARTUM,         //산후
}
