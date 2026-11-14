package com.dangdang.domain.post.enums;

// 게시판. API 글자이자 DB에 저장되는 값
public enum PostCategory {
    NEWS,         // 당당 소식 (공식 계정만)
    MEAL,         // 식단
    MEASUREMENT,  // 측정·알림
    VISIT,        // 진료 후기
    MIND,         // 마음
    BIRTH,        // 출산
    POSTPARTUM    // 출산·산후 탭
}
