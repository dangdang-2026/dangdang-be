package com.dangdang.domain.card.entity;

import com.dangdang.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "card_saves")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardSave {

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

    public static CardSave of(User user, ContentCard card, Instant now) {
        CardSave s = new CardSave();
        s.user = user;
        s.card = card;
        s.createdAt = now;
        return s;
    }
}
