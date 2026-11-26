package com.dangdang.domain.food.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 주식 여부 규칙의 경계 (분류는 equals, 빵은 대표식품명 목록 equals + 「샌드위치」 contains, 주식 분류의 반찬은 contains)
// DB 없이 규칙만 봄 → 나중에 목록을 고치다 경계가 바뀌면 여기서 잡힘
class FoodStapleTest {

    @Test
    void 빵은_대표식품명이_목록과_같아야_주식이고_샌드위치는_들어만_있어도_주식() {
        assertThat(Food.isStaple("빵 및 과자류", "식빵")).isTrue();
        assertThat(Food.isStaple("빵 및 과자류", "피자")).isTrue();
        assertThat(Food.isStaple("빵 및 과자류", "피자빵")).isFalse();              // 목록과 똑같지 않음 (간식 빵)
        assertThat(Food.isStaple("빵 및 과자류", "소세지빵")).isFalse();
        assertThat(Food.isStaple("빵 및 과자류", "감자샐러드 샌드위치")).isTrue();  // 샌드위치는 포함 검사
        assertThat(Food.isStaple("빵 및 과자류", "케이크")).isFalse();
        assertThat(Food.isStaple("빵 및 과자류", "시리얼")).isTrue();               // 아침 끼니 (프론트 요청)
    }

    @Test
    void 주식_분류_안의_스프_만두_월남쌈은_주식이_아니고_만두국은_주식() {
        assertThat(Food.isStaple("죽 및 스프류", "호박죽")).isTrue();
        assertThat(Food.isStaple("죽 및 스프류", "감자스프")).isFalse();
        assertThat(Food.isStaple("면 및 만두류", "군만두")).isFalse();
        assertThat(Food.isStaple("면 및 만두류", "만두탕수")).isFalse();
        assertThat(Food.isStaple("면 및 만두류", "월남쌈")).isFalse();
        assertThat(Food.isStaple("면 및 만두류", "떡만두국")).isTrue();
        assertThat(Food.isStaple("면 및 만두류", "칼국수")).isTrue();
        assertThat(Food.isStaple("밥류", null)).isTrue();                          // 대표식품명이 없으면 분류만 봄
    }

    @Test
    void 분류나_빵의_대표식품명이_없으면_주식이_아님() {
        assertThat(Food.isStaple(null, "식빵")).isFalse();
        assertThat(Food.isStaple("빵 및 과자류", null)).isFalse();
        assertThat(Food.isStaple("국 및 탕류", "갈비탕")).isFalse();
    }
}
