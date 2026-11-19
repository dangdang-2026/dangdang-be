package com.dangdang.domain.comment.repository;

import com.dangdang.domain.comment.entity.Comment;
import com.dangdang.global.common.IdCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Optional<Comment> findByIdAndDeletedAtIsNull(Long id);

    // 지운 댓글·가린 댓글·내가 안 보기 한 사람의 댓글, 그리고 그 밑의 답글까지 뺀다. 오래된 순
    @Query("""
            select c from Comment c
            left join c.parent p
            where c.post.id = :postId
              and c.deletedAt is null and c.hiddenReason is null
              and (p is null or (p.deletedAt is null and p.hiddenReason is null))
              and not exists (select 1 from ProfileBlock b
                              where b.profile.id = :viewer
                                and (b.blockedProfile.id = c.profile.id or b.blockedProfile.id = p.profile.id))
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
                                and (b.blockedProfile.id = c.profile.id or b.blockedProfile.id = p.profile.id))
            group by c.post.id
            """)
    List<IdCount> countVisibleByPostIds(@Param("postIds") Collection<Long> postIds, @Param("viewer") Long viewerProfileId);

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
}
