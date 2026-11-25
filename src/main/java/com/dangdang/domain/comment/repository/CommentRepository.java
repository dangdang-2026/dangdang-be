package com.dangdang.domain.comment.repository;

import com.dangdang.domain.comment.entity.Comment;
import com.dangdang.global.common.IdCount;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Optional<Comment> findByIdAndDeletedAtIsNull(Long id);

    // 지운 댓글·가린 댓글·내가 안 보기 한 댓글, 그리고 그 밑의 답글까지 뺀다. 오래된 순
    // 안 보기는 이름 차단 줄 = 이름 댓글만, 익명 차단 줄 = 익명 댓글만 (BlockService.isBlocking과 같은 규칙)
    @Query("""
            select c from Comment c
            left join c.parent p
            where c.post.id = :postId
              and c.deletedAt is null and c.hiddenReason is null
              and (p is null or (p.deletedAt is null and p.hiddenReason is null))
              and not exists (select 1 from ProfileBlock b
                              where b.profile.id = :viewer
                                and ((b.blockedProfile.id = c.profile.id and b.viaAnonymous = c.anonymous)
                                     or (b.blockedProfile.id = p.profile.id and b.viaAnonymous = p.anonymous)))
            order by c.createdAt asc, c.id asc
            """)
    List<Comment> findVisibleByPostId(@Param("postId") Long postId, @Param("viewer") Long viewerProfileId);

    // 목록·상세의 commentCount — 위 목록과 같은 조건으로 센다
    @Query("""
            select c.post.id as id, count(c) as cnt from Comment c
            left join c.parent p
            where c.post.id in :postIds
              and c.deletedAt is null and c.hiddenReason is null
              and (p is null or (p.deletedAt is null and p.hiddenReason is null))
              and not exists (select 1 from ProfileBlock b
                              where b.profile.id = :viewer
                                and ((b.blockedProfile.id = c.profile.id and b.viaAnonymous = c.anonymous)
                                     or (b.blockedProfile.id = p.profile.id and b.viaAnonymous = p.anonymous)))
            group by c.post.id
            """)
    List<IdCount> countVisibleByPostIds(@Param("postIds") Collection<Long> postIds, @Param("viewer") Long viewerProfileId);

    // ── U-02b 「익명 1·2」 번호 ──
    // 이 글의 번호 매기기를 한 사람씩 차례로 (트랜잭션 끝까지). 두 사람이 동시에 첫 익명 댓글을 달아도 번호가 겹치지 않는다
    // 글 줄(posts)을 잠그지 않아서 이름 댓글·조회 수·운영자 가리기는 기다리지 않는다
    @Query(nativeQuery = true, value = """
            SELECT 1 FROM pg_advisory_xact_lock(hashtext('comments.anonymous_no'), CAST(:postId % 2147483647 AS integer))
            """)
    Integer lockAnonymousNumbers(@Param("postId") Long postId);

    // 이 사람이 이 글에서 이미 받은 번호 (지운·가린 댓글 것도 그대로 쓴다)
    @Query(nativeQuery = true, value = """
            SELECT anonymous_no FROM comments
            WHERE post_id = :postId AND profile_id = :profileId AND anonymous_no IS NOT NULL
            LIMIT 1
            """)
    Optional<Integer> findAnonymousNo(@Param("postId") Long postId, @Param("profileId") Long profileId);

    // 새 번호 = 이 글에서 준 가장 큰 번호 + 1 (탈퇴해서 profile_id가 비어도 번호는 남아 있어 다시 안 쓴다)
    @Query(nativeQuery = true, value = "SELECT COALESCE(MAX(anonymous_no), 0) + 1 FROM comments WHERE post_id = :postId")
    int nextAnonymousNo(@Param("postId") Long postId);

    // ── U-23 인기 카드 아래 베스트 댓글 ── 글마다 하나, BestComment.pick과 같은 규칙
    // 답글 빼고 · 지운·가린 댓글 빼고 · 내가 안 보기 한 사람 빼고 · 공감 minReactions 이상 · 많은 순 → 먼저 단 순 → id
    // 이 글들의 댓글 공감만 센다 (ix_comments_post_time · uk_comment_reactions)
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT ON (c.post_id) c.id AS id, count(*) AS cnt
            FROM comments c
            JOIN comment_reactions r ON r.comment_id = c.id
            WHERE c.post_id IN (:postIds)
              AND c.parent_id IS NULL AND c.deleted_at IS NULL AND c.hidden_reason IS NULL
              AND NOT EXISTS (SELECT 1 FROM profile_blocks b
                              WHERE b.profile_id = :viewer AND b.blocked_profile_id = c.profile_id
                                AND b.via_anonymous = c.is_anonymous)
            GROUP BY c.post_id, c.id, c.created_at
            HAVING count(*) >= :minReactions
            ORDER BY c.post_id, count(*) DESC, c.created_at, c.id
            """)
    List<IdCount> findBestComments(@Param("postIds") Collection<Long> postIds, @Param("viewer") Long viewerProfileId,
                                   @Param("minReactions") int minReactions);

    // 베스트 댓글 한 줄에 글쓴이 이름을 붙이려고 프로필까지 한 번에
    @Query("select c from Comment c left join fetch c.profile where c.id in :ids")
    List<Comment> findWithProfileByIdIn(@Param("ids") Collection<Long> ids);

    // 운영자가 가리기·문제없음·되돌리기를 하기 전에 이 댓글 줄을 잠근다 (트랜잭션 끝까지)
    // 같은 대상을 두 운영자가 동시에 처리해도 차례로 → 가림 상태와 신고 상태가 어긋나지 않는다. 줄이 없으면 empty
    @Query(nativeQuery = true, value = "SELECT id FROM comments WHERE id = :id FOR UPDATE")
    Optional<Long> lockForAdmin(@Param("id") Long id);

    // 운영자 가리기 — 아직 안 가린 댓글만 바꾼다. 이미 가렸거나 지웠거나 댓글이 없으면 0줄
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true,
            value = "UPDATE comments SET hidden_reason = :reason WHERE id = :id AND hidden_reason IS NULL AND deleted_at IS NULL")
    int hide(@Param("id") Long id, @Param("reason") String reason);

    // 운영자 되돌리기 — 가린 댓글만 푼다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true,
            value = "UPDATE comments SET hidden_reason = NULL WHERE id = :id AND hidden_reason IS NOT NULL")
    int unhide(@Param("id") Long id);

    // ── 마이 › 내가 쓴 댓글 (M-25) ──
    // 지운·가린 댓글, 지운·가린 글의 댓글, 내가 안 보기 한 사람 글의 댓글은 뺀다 (눌러도 그 글이 404라서). 최신 먼저
    @Query("""
            select c from Comment c join fetch c.post p
            where c.profile.id = :profileId
              and c.deletedAt is null and c.hiddenReason is null
              and p.deletedAt is null and p.hiddenReason is null
              and not exists (select 1 from ProfileBlock b
                              where b.profile.id = :profileId and b.blockedProfile.id = p.profile.id
                                and b.viaAnonymous = p.anonymous)
            order by c.createdAt desc, c.id desc
            """)
    List<Comment> findMine(@Param("profileId") Long profileId, Limit limit);

    // 다음 페이지 — 지난 페이지 마지막 댓글(at, id)보다 뒤의 것만
    @Query("""
            select c from Comment c join fetch c.post p
            where c.profile.id = :profileId
              and c.deletedAt is null and c.hiddenReason is null
              and p.deletedAt is null and p.hiddenReason is null
              and not exists (select 1 from ProfileBlock b
                              where b.profile.id = :profileId and b.blockedProfile.id = p.profile.id
                                and b.viaAnonymous = p.anonymous)
              and (c.createdAt < :at or (c.createdAt = :at and c.id < :id))
            order by c.createdAt desc, c.id desc
            """)
    List<Comment> findMineBefore(@Param("profileId") Long profileId, @Param("at") Instant at, @Param("id") Long id,
                                 Limit limit);

    // M-14 「내가 쓴 댓글 N개」 — 위 목록과 같은 조건으로 센다
    @Query("""
            select count(c) from Comment c join c.post p
            where c.profile.id = :profileId
              and c.deletedAt is null and c.hiddenReason is null
              and p.deletedAt is null and p.hiddenReason is null
              and not exists (select 1 from ProfileBlock b
                              where b.profile.id = :profileId and b.blockedProfile.id = p.profile.id
                                and b.viaAnonymous = p.anonymous)
            """)
    long countMine(@Param("profileId") Long profileId);
}
