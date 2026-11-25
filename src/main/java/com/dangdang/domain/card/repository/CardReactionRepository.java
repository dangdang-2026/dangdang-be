package com.dangdang.domain.card.repository;

import com.dangdang.domain.card.entity.CardReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface CardReactionRepository extends JpaRepository<CardReaction, Long> {

    boolean existsByUserIdAndCardId(Long userId, Long cardId);

    // 두 번 눌러도, 동시에 눌러도 한 줄 (CardSaveRepository.addIfAbsent와 같은 방식)
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO card_reactions (user_id, card_id, created_at)
            VALUES (:userId, :cardId, :now)
            ON CONFLICT (user_id, card_id) DO NOTHING
            """)
    void addIfAbsent(@Param("userId") Long userId, @Param("cardId") Long cardId, @Param("now") Instant now);

    @Modifying
    @Query("delete from CardReaction r where r.user.id = :userId and r.card.id = :cardId")
    void remove(@Param("userId") Long userId, @Param("cardId") Long cardId);
}
