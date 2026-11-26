package com.dangdang.domain.shop.entity;

import com.dangdang.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// product_visits 한 줄 = 판매처로 보러 간 기록 하나 (M-22). 샀는지는 모른다
// 가격·판매처 이름은 그때 값을 복사해 둔다
@Getter
@Entity
@Table(name = "product_visits")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private Integer price;

    @Column(nullable = false, length = 50)
    private String sellerName;

    @Column(nullable = false)
    private Instant visitedAt;

    public static ProductVisit of(User user, Product product, Instant now) {
        ProductVisit v = new ProductVisit();
        v.user = user;
        v.product = product;
        v.price = product.getPrice();
        v.sellerName = product.getSellerName();
        v.visitedAt = now;
        return v;
    }

    // 조금 전에 같은 상품으로 간 기록이 있으면 새 줄 대신 시각·가격만 새로 (두 번 눌러도 한 줄)
    public void revisit(Product product, Instant now) {
        this.price = product.getPrice();
        this.sellerName = product.getSellerName();
        this.visitedAt = now;
    }
}
