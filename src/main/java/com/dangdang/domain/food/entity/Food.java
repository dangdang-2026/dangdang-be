package com.dangdang.domain.food.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

// foods 테이블 한 줄 = 식약처 음식 하나. 마이그레이션으로만 넣는 공공 데이터라 앱에서는 읽기만 함
// 영양값은 기준량(100g 또는 100ml)당 값이고, 모르는 값은 null (0과 구분)
@Getter
@Entity
@Immutable
@Table(name = "foods")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Food {

    @Id
    @Column(length = 30)
    private String foodCode;                 // 식약처 식품코드 (예: D102-053000000-0001)

    @Column(nullable = false, length = 100)
    private String name;                     // 식품명 (예: 가래떡_흑미)

    @Column(length = 100)
    private String repName;                  // 대표식품명 (예: 가래떡)

    @Column(length = 30)
    private String category;                 // 식품대분류명

    @Column(nullable = false)
    private Short origin;                    // 식품기원코드 1 가정식 / 2 프랜차이즈 / 3·4 외식 / 5~7 급식

    @Column(length = 100)
    private String maker;                    // 업체명 (프랜차이즈만)

    @Column(nullable = false, length = 2)
    private String baseUnit;                 // g / ml

    @Column(precision = 7, scale = 1)
    private BigDecimal servingAmount;        // 1인분 양 (baseUnit 단위). 모르면 null

    @Column(precision = 7, scale = 2)
    private BigDecimal kcal;

    @Column(name = "carb_g", precision = 7, scale = 2)   // 이름 끝 대문자 하나는 자동으로 _g가 안 돼서 직접 적음
    private BigDecimal carbG;

    @Column(name = "sugar_g", precision = 7, scale = 2)   // 이름 끝 대문자 하나는 자동으로 _g가 안 돼서 직접 적음
    private BigDecimal sugarG;

    @Column(name = "protein_g", precision = 7, scale = 2)   // 이름 끝 대문자 하나는 자동으로 _g가 안 돼서 직접 적음
    private BigDecimal proteinG;

    @Column(name = "fat_g", precision = 7, scale = 2)   // 이름 끝 대문자 하나는 자동으로 _g가 안 돼서 직접 적음
    private BigDecimal fatG;

    @Column(nullable = false)
    private LocalDate sourceVersion;         // 데이터기준일

    private static final short FRANCHISE = 2;
    private static final String MEAL_KIT = "간편조리세트";
    // 이름 뒤 조각이 이것뿐이면 재료가 아니라 양념이라 괄호로 뺌 (쑥갓나물_고춧가루 → 쑥갓나물 (고춧가루))
    private static final Set<String> SEASONINGS = Set.of("간장", "소금", "된장", "고추장", "고춧가루", "양념");

    // 화면에 보여 줄 이름. 식약처 이름은 "종류_재료" 순서라서 사람이 부르는 순서로 바꿈
    //   볶음밥_김치 → 김치 볶음밥 / 샌드위치_햄_치즈 → 햄 치즈 샌드위치
    //   뒤 조각에 종류가 이미 들어 있으면 뒤 조각만: 식빵_통밀식빵 → 통밀식빵, 파이/만주_사과파이 → 사과파이
    //   종류에 이미 든 조각은 뺌: 부추무침_부추_채소 → 채소 부추무침
    //   2글자 이상 겹치면 한 단어로: 버섯볶음_새송이버섯 → 새송이버섯볶음
    //   양념은 괄호로: 쑥갓나물_고춧가루 → 쑥갓나물 (고춧가루)
    //   간편조리세트는 뒤가 상품명이라 상품명만 + 표시: 칼국수_간편조리세트_원주식장칼국수 → 원주식장칼국수 (간편조리세트)
    //   프랜차이즈는 뒤가 상품명이라 상품명만: 커피_아메리카노 핫(HOT) → 아메리카노 핫(HOT)
    public String displayName() {
        List<String> parts = Arrays.stream(name.split("_")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (parts.size() < 2) {
            return name.trim();
        }
        String kind = parts.get(0);
        List<String> rest = parts.subList(1, parts.size());

        boolean mealKit = rest.contains(MEAL_KIT);
        if (origin == FRANCHISE || mealKit) {
            List<String> product = rest.stream().filter(p -> !p.equals(MEAL_KIT)).toList();
            String shown = product.isEmpty() ? kind : String.join(" ", product);
            return mealKit ? shown + " (" + MEAL_KIT + ")" : shown;
        }

        List<String> seasonings = rest.stream().filter(SEASONINGS::contains).toList();
        // 종류 이름에 이미 들어 있는 조각은 뺌 (부추무침_부추_채소 → 채소 부추무침, "부추"가 두 번 안 나오게)
        List<String> others = rest.stream().filter(p -> !SEASONINGS.contains(p) && !kind.contains(p)).toList();
        String core;
        if (others.isEmpty()) {
            core = kind;
        } else if (containsKind(others, kind)) {
            core = String.join(" ", others);
        } else {
            core = mergeOverlap(others, kind);
        }
        return seasonings.isEmpty() ? core : core + " (" + String.join(", ", seasonings) + ")";
    }

    // 뒤 조각 끝과 종류 앞이 2글자 이상 겹치면 한 단어로 붙임 (버섯볶음_새송이버섯 → 새송이버섯볶음, 만두국_김치만두 → 김치만두국)
    // 1글자 겹침은 "미음_현미 → 현미음"처럼 틀린 말이 돼서 안 붙임. 안 겹치면 그냥 앞에 둠 (볶음밥_김치 → 김치 볶음밥)
    private static String mergeOverlap(List<String> others, String kind) {
        for (String p : others) {
            for (int len = Math.min(p.length() - 1, kind.length() - 1); len >= 2; len--) {
                if (kind.startsWith(p.substring(p.length() - len))) {
                    List<String> left = others.stream().filter(o -> !o.equals(p)).toList();
                    String merged = p + kind.substring(len);
                    return left.isEmpty() ? merged : String.join(" ", left) + " " + merged;
                }
            }
        }
        return String.join(" ", others) + " " + kind;
    }

    // 뒤 조각 중 하나에 종류 이름이 들어 있는지. 종류가 "파이/만주"처럼 여러 개면 하나라도 들어 있으면 됨
    private static boolean containsKind(List<String> others, String kind) {
        String[] kinds = kind.split("/");
        return others.stream().anyMatch(p -> Arrays.stream(kinds).anyMatch(k -> !k.isBlank() && p.contains(k.trim())));
    }
}
