package com.dangdang.domain.post.repository;

import com.dangdang.domain.post.entity.PostReaction;
import com.dangdang.global.common.IdCount;
import org.springframework.data.domain.Limit;
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

    // ── 마이 › 공감한 글 (M-26) ──
    // 지운·가린 글, 내가 안 보기 한 사람의 글은 뺀다 (눌러도 404라서). 공감을 누른 때가 최신인 것 먼저
    @Query("""
            select r from PostReaction r join fetch r.post p
            where r.profile.id = :profileId
              and p.deletedAt is null and p.hiddenReason is null
              and not exists (select 1 from ProfileBlock b
                              where b.profile.id = :profileId and b.blockedProfile.id = p.profile.id)
            order by r.createdAt desc, r.id desc
            """)
    List<PostReaction> findMine(@Param("profileId") Long profileId, Limit limit);

    // 다음 페이지 — 지난 페이지 마지막 공감(at, id)보다 뒤의 것만
    @Query("""
            select r from PostReaction r join fetch r.post p
            where r.profile.id = :profileId
              and p.deletedAt is null and p.hiddenReason is null
              and not exists (select 1 from ProfileBlock b
                              where b.profile.id = :profileId and b.blockedProfile.id = p.profile.id)
              and (r.createdAt < :at or (r.createdAt = :at and r.id < :id))
            order by r.createdAt desc, r.id desc
            """)
    List<PostReaction> findMineBefore(@Param("profileId") Long profileId, @Param("at") Instant at,
                                      @Param("id") Long id, Limit limit);

    // M-14 「공감한 글 N개」 — 위 목록과 같은 조건으로 센다
    @Query("""
            select count(r) from PostReaction r join r.post p
            where r.profile.id = :profileId
              and p.deletedAt is null and p.hiddenReason is null
              and not exists (select 1 from ProfileBlock b
                              where b.profile.id = :profileId and b.blockedProfile.id = p.profile.id)
            """)
    long countMine(@Param("profileId") Long profileId);
}
