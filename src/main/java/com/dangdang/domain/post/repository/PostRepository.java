package com.dangdang.domain.post.repository;

import com.dangdang.domain.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {

    Optional<Post> findByIdAndDeletedAtIsNull(Long id);

    // 동시에 읽어도 숫자가 빠지지 않게 DB에서 바로 +1
    @Modifying
    @Query("update Post p set p.viewCount = p.viewCount + 1 where p.id = :id")
    void increaseViewCount(@Param("id") Long id);

    // 운영자 가리기 — 아직 안 가린 글만 바꾼다. 이미 가렸거나 글이 없으면 0줄
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true,
            value = "UPDATE posts SET hidden_reason = :reason WHERE id = :id AND hidden_reason IS NULL")
    int hide(@Param("id") Long id, @Param("reason") String reason);

    // 운영자 되돌리기 — 가린 글만 푼다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true,
            value = "UPDATE posts SET hidden_reason = NULL WHERE id = :id AND hidden_reason IS NOT NULL")
    int unhide(@Param("id") Long id);
}
