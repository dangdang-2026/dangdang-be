package com.dangdang.domain.chat.repository;

import com.dangdang.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 대화 목록 — 최신이 먼저
    List<ChatMessage> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long id, Limit limit);

    // AI에 같이 보낼 바로 앞 대화 (최신 6개)
    List<ChatMessage> findTop6ByUserIdOrderByIdDesc(Long userId);

    // "내 것만" — 남의 번호면 비어서 나온다
    Optional<ChatMessage> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("delete from ChatMessage m where m.user.id = :userId")
    int deleteAllByUserId(Long userId);
}
