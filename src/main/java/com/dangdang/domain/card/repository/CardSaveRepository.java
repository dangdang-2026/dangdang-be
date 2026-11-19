package com.dangdang.domain.card.repository;

import com.dangdang.domain.card.entity.CardSave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface CardSaveRepository extends JpaRepository<CardSave, Long> {

    boolean existsByUserIdAndCardId(Long userId, Long cardId);

    // 이미 있으면 아무 일도 안 한다 — 두 번 눌러도, 동시에 눌러도 한 줄 (시각은 앱 Clock에서 받은 값)
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO card_saves (user_id, card_id, created_at)
            VALUES (:userId, :cardId, :now)
            ON CONFLICT (user_id, card_id) DO NOTHING
            """)
    void addIfAbsent(@Param("userId") Long userId, @Param("cardId") Long cardId, @Param("now") Instant now);

    // 저장 취소 — 없으면 0줄. 두 번 빠르게 눌러도 둘 다 성공한다 (읽고 지우면 두 번째가 0줄 삭제로 500)
    @Modifying
    @Query("delete from CardSave s where s.user.id = :userId and s.card.id = :cardId")
    void remove(@Param("userId") Long userId, @Param("cardId") Long cardId);

    // 내가 저장한 카드 — 최근에 저장한 순 (카드도 같이 읽는다)
    @Query("select s from CardSave s join fetch s.card where s.user.id = :userId order by s.createdAt desc, s.id desc")
    List<CardSave> findMine(@Param("userId") Long userId);

    @Query("select s.card.id from CardSave s where s.user.id = :userId and s.card.id in :cardIds")
    Set<Long> findSavedCardIds(@Param("userId") Long userId, @Param("cardIds") Collection<Long> cardIds);
}
