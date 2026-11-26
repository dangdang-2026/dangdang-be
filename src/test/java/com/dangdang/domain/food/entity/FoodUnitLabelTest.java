package com.dangdang.domain.food.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// 표시 단위 규칙 (분류별 단위 + 대표식품명 예외). DB 없이 규칙만 봄
class FoodUnitLabelTest {

    @Test
    void 분류마다_단위() {
        assertThat(Food.unitLabelOf("밥류", "볶음밥")).isEqualTo("그릇");
        assertThat(Food.unitLabelOf("죽 및 스프류", "호박죽")).isEqualTo("그릇");
        assertThat(Food.unitLabelOf("국 및 탕류", "미역국")).isEqualTo("그릇");
        assertThat(Food.unitLabelOf("찌개 및 전골류", "김치찌개")).isEqualTo("그릇");
        assertThat(Food.unitLabelOf("면 및 만두류", "칼국수")).isEqualTo("그릇");
        assertThat(Food.unitLabelOf("전·적 및 부침류", "김치전")).isEqualTo("접시");
        assertThat(Food.unitLabelOf("볶음류", "감자볶음")).isEqualTo("접시");
        assertThat(Food.unitLabelOf("생채·무침류", "도라지생채")).isEqualTo("접시");
        assertThat(Food.unitLabelOf("나물·숙채류", "시금치나물")).isEqualTo("접시");
        assertThat(Food.unitLabelOf("조림류", "감자조림")).isEqualTo("접시");
        assertThat(Food.unitLabelOf("김치류", "배추김치")).isEqualTo("접시");
        assertThat(Food.unitLabelOf("장아찌·절임류", "깻잎장아찌")).isEqualTo("접시");
        assertThat(Food.unitLabelOf("젓갈류", "명란젓")).isEqualTo("접시");
        assertThat(Food.unitLabelOf("음료 및 차류", "아메리카노")).isEqualTo("잔");
    }

    @Test
    void 한_분류에_양이_섞인_분류는_인분() {
        assertThat(Food.unitLabelOf("찜류", "돼지갈비찜")).isEqualTo("인분");
        assertThat(Food.unitLabelOf("구이류", "고등어구이")).isEqualTo("인분");
        assertThat(Food.unitLabelOf("튀김류", "닭튀김")).isEqualTo("인분");
        assertThat(Food.unitLabelOf("빵 및 과자류", "피자")).isEqualTo("인분");
        assertThat(Food.unitLabelOf("유제품류 및 빙과류", "아이스크림")).isEqualTo("인분");
        assertThat(Food.unitLabelOf("장류, 양념류", "쌈장")).isEqualTo("인분");
    }

    @Test
    void 밥류_안의_예외() {
        assertThat(Food.unitLabelOf("밥류", "쌀밥")).isEqualTo("공기");
        assertThat(Food.unitLabelOf("밥류", "잡곡밥")).isEqualTo("공기");
        assertThat(Food.unitLabelOf("밥류", "순 현미밥")).isEqualTo("공기");
        assertThat(Food.unitLabelOf("밥류", "묵밥")).isEqualTo("그릇");           // 「밥」으로 끝나도 국물 있는 한 그릇
        assertThat(Food.unitLabelOf("밥류", "김밥")).isEqualTo("줄");
        assertThat(Food.unitLabelOf("밥류", "삼각김밥")).isEqualTo("개");
        assertThat(Food.unitLabelOf("밥류", "모듬 김밥")).isEqualTo("인분");
        assertThat(Food.unitLabelOf("밥류", "주먹밥")).isEqualTo("인분");
        assertThat(Food.unitLabelOf("밥류", "초밥")).isEqualTo("인분");
    }

    @Test
    void 다른_분류_안의_예외() {
        assertThat(Food.unitLabelOf("찌개 및 전골류", "소고기전골")).isEqualTo("인분");
        assertThat(Food.unitLabelOf("찌개 및 전골류", "버섯 샤브샤브")).isEqualTo("인분");
        assertThat(Food.unitLabelOf("면 및 만두류", "군만두")).isEqualTo("인분");
        assertThat(Food.unitLabelOf("면 및 만두류", "떡만두국")).isEqualTo("그릇");
        assertThat(Food.unitLabelOf("면 및 만두류", "월남쌈")).isEqualTo("인분");
        assertThat(Food.unitLabelOf("전·적 및 부침류", "달걀부침(달걀후라이)")).isEqualTo("개");
        assertThat(Food.unitLabelOf("전·적 및 부침류", "달걀말이")).isEqualTo("인분");
        assertThat(Food.unitLabelOf("찜류", "달걀찜")).isEqualTo("그릇");
        assertThat(Food.unitLabelOf("찜류", "두부 달걀찜")).isEqualTo("그릇");
    }

    @Test
    void 일인분_양을_모르면_인분() {
        assertThat(Food.unitLabelOf("국 및 탕류", "미역국", null)).isEqualTo("인분");   // servings 1 = 100g이라 「1그릇」이 안 맞음
        assertThat(Food.unitLabelOf("볶음류", "감자볶음", null)).isEqualTo("인분");
        assertThat(Food.unitLabelOf("국 및 탕류", "미역국", new BigDecimal("240"))).isEqualTo("그릇");
    }

    @Test
    void 공기_줄_개는_일인분_양이_그_개수에_맞을_때만() {
        assertThat(Food.unitLabelOf("밥류", "쌀밥", new BigDecimal("100"))).isEqualTo("인분");     // 반 공기
        assertThat(Food.unitLabelOf("밥류", "쌀밥", new BigDecimal("150"))).isEqualTo("공기");     // 경계 포함
        assertThat(Food.unitLabelOf("밥류", "잡곡밥", new BigDecimal("300"))).isEqualTo("공기");
        assertThat(Food.unitLabelOf("밥류", "잡곡밥", new BigDecimal("350"))).isEqualTo("공기");
        assertThat(Food.unitLabelOf("밥류", "잡곡밥", new BigDecimal("450"))).isEqualTo("인분");   // 두 공기 가까이
        assertThat(Food.unitLabelOf("밥류", "김밥", new BigDecimal("100"))).isEqualTo("인분");     // 김밥_참치 100g
        assertThat(Food.unitLabelOf("밥류", "김밥", new BigDecimal("250"))).isEqualTo("줄");
        assertThat(Food.unitLabelOf("밥류", "김밥", new BigDecimal("400"))).isEqualTo("줄");
        assertThat(Food.unitLabelOf("밥류", "삼각김밥", new BigDecimal("100"))).isEqualTo("개");
        assertThat(Food.unitLabelOf("밥류", "삼각김밥", new BigDecimal("200"))).isEqualTo("인분");  // 두 개
        assertThat(Food.unitLabelOf("전·적 및 부침류", "달걀후라이", new BigDecimal("60"))).isEqualTo("개");
        assertThat(Food.unitLabelOf("볶음류", "감자볶음", new BigDecimal("1100"))).isEqualTo("접시"); // 그릇·접시·잔은 양을 안 봄
    }

    @Test
    void 분류가_없거나_비었거나_모르는_값이면_인분() {
        assertThat(Food.unitLabelOf(null, "쌀밥")).isEqualTo("인분");
        assertThat(Food.unitLabelOf("", "쌀밥")).isEqualTo("인분");
        assertThat(Food.unitLabelOf("  ", null)).isEqualTo("인분");
        assertThat(Food.unitLabelOf("새로 생긴 분류", "쌀밥")).isEqualTo("인분");
        assertThat(Food.unitLabelOf("국 및 탕류", null)).isEqualTo("그릇");      // 대표식품명이 없으면 분류만 봄
        assertThat(Food.unitLabelOf("국 및 탕류", "")).isEqualTo("그릇");
    }
}
