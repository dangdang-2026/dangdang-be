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
}
