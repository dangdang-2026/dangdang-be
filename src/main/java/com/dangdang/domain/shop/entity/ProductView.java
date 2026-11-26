package com.dangdang.domain.shop.entity;

import com.dangdang.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// product_views 한 줄 = 최근 본 상품 하나. 한 사람·한 상품에 한 줄 (넣기는 ProductViewRepository.upsert)
@Getter
@Entity
@Table(name = "product_views")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductView {

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
    private Instant viewedAt;
}
