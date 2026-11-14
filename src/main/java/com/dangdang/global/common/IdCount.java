package com.dangdang.global.common;

// "id별 개수" 조회 결과를 받는 그릇 (group by 쿼리용)
public interface IdCount {
    Long getId();

    long getCnt();
}
