package com.dangdang.domain.shop.enums;

// 상품 혈당 후기 목록 필터 (be #192)
public enum ProductReviewFilter {
    ALL,          // 전체
    ONE_HOUR,     // 식후 1시간에 잰 것 (잴 때 병원 기준이 60분)
    TWO_HOUR,     // 식후 2시간에 잰 것 (120분)
    NO_INSULIN    // 인슐린을 안 쓰는 사람 (지금 임신 정보의 관리 방법이 인슐린이 아님)
}
