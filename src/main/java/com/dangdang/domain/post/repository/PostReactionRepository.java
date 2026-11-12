package com.dangdang.domain.post.repository;

import com.dangdang.domain.post.entity.PostReaction;
import com.dangdang.global.common.IdCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface PostReactionRepository extends JpaRepository<PostReaction, Long> {

    // 이미 있으면 아무 일도 안 한다 — 두 번 눌러도, 동시에 눌러도 한 줄
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO post_reactions (post_id, profile_id, created_at)
            VALUES (:postId, :profileId, :now)
            ON CONFLICT (post_id, profile_id) DO NOTHING
            """)
    void addIfAbsent(@Param("postId") Long postId, @Param("profileId") Long profileId, @Param("now") Instant now);

    @Modifying
    @Query("delete from PostReaction r where r.post.id = :postId and r.profile.id = :profileId")
    void remove(@Param("postId") Long postId, @Param("profileId") Long profileId);

    long countByPostId(Long postId);

    boolean existsByPostIdAndProfileId(Long postId, Long profileId);

    @Query("select r.post.id as id, count(r) as cnt from PostReaction r where r.post.id in :postIds group by r.post.id")
    List<IdCount> countByPostIds(@Param("postIds") Collection<Long> postIds);

    @Query("select r.post.id from PostReaction r where r.post.id in :postIds and r.profile.id = :profileId")
    Set<Long> findReactedPostIds(@Param("postIds") Collection<Long> postIds, @Param("profileId") Long profileId);
}
