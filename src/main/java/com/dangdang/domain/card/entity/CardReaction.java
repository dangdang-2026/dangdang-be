package com.dangdang.domain.card.entity;

import com.dangdang.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// 카드 공감 — 누른 사람만 안다 (숫자는 어디에도 안 보여 준다)
@Getter
@Entity
@Table(name = "card_reactions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id")
    private ContentCard card;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
