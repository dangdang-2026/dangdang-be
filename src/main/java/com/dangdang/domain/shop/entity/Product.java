package com.dangdang.domain.shop.entity;

import com.dangdang.domain.shop.enums.ProductCategory;
import com.dangdang.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// products 한 줄 = 상점 상품 하나. 운영자가 넣고, 앱은 읽기만 한다 (결제·배송 없음 → 판매처 링크)
@Getter
@Entity
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductCategory category;

    @Column(nullable = false, length = 50)
    private String brand;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 50)
    private String sizeLabel;                // 「100g × 4」

    @Column(nullable = false)
    private Integer price;                   // 판매처 가격(원)

    private Integer contentAmount;           // 총 용량 숫자 (100g × 4 → 400). 모르면 null

    @Column(length = 2)
    private String contentUnit;              // g · ml

    @Column(length = 30)
    private String shippingNote;             // 「무료배송」

    @Column(length = 20)
    private String servingLabel;             // 영양 기준 「1개」

    @Column(precision = 7, scale = 2)
    private BigDecimal kcal;

    @Column(name = "carb_g", precision = 7, scale = 2)      // 끝이 대문자 한 글자라 자동으로 carb_g 가 안 돼서 직접 적음 (Food 와 같음)
    private BigDecimal carbG;

    @Column(name = "sugar_g", precision = 7, scale = 2)
    private BigDecimal sugarG;

    @Column(name = "protein_g", precision = 7, scale = 2)
    private BigDecimal proteinG;

    @JdbcTypeCode(SqlTypes.JSON)             // DB 칸이 JSONB라서
    @Column(nullable = false)
    private List<String> imageUrls = new ArrayList<>();   // 첫 번째가 대표 사진

    @Column(nullable = false, length = 50)
    private String sellerName;

    @Column(nullable = false, length = 500)
    private String sellerUrl;                // https + 호스트만 (DB CHECK). 응답엔 POST …/visits 로만 나감

    @Column(nullable = false)
    private boolean affiliate;               // 제휴 링크 → 화면에 「제휴」

    @Column(nullable = false)
    private boolean pick;                    // 이번 주 산모 PICK

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;                  // 내린 상품 = false (지우지 않음)

    // 사진들. 운영자가 SQL로 넣다가 null·빈 칸이 섞여도 응답이 500 나지 않게 걸러서 준다
    public List<String> images() {
        return imageUrls == null ? List.of()
                : imageUrls.stream().filter(Objects::nonNull).filter(u -> !u.isBlank()).toList();
    }

    // 대표 사진 (없으면 null → 프론트는 회색 자리)
    public String mainImageUrl() {
        List<String> images = images();
        return images.isEmpty() ? null : images.get(0);
    }

    // P-02 「100g당 2,450원」. 용량을 모르면 null (그 글자 숨김). 원 단위 반올림
    public Integer pricePer100() {
        if (contentAmount == null) return null;
        return Math.toIntExact(Math.round(price * 100.0 / contentAmount));
    }

    // 영양 칸이 하나라도 있으면 P-02 「영양」 줄을 보여 준다
    public boolean hasNutrition() {
        return kcal != null || carbG != null || sugarG != null || proteinG != null;
    }
}
