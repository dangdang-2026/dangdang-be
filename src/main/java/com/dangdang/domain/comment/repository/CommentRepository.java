package com.dangdang.domain.comment.repository;

import com.dangdang.domain.comment.entity.Comment;
import com.dangdang.global.common.IdCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Optional<Comment> findByIdAndDeletedAtIsNull(Long id);

    // 지운 댓글&가린 댓글, 그리고 그 밑의 답글까지 뺀다. 오래된 순
    @Query("""
            select c from Comment c
            left join c.parent p
            where c.post.id = :postId
              and c.deletedAt is null and c.hiddenReason is null
              and (p is null or (p.deletedAt is null and p.hiddenReason is null))
            order by c.createdAt asc, c.id asc
            """)
    List<Comment> findVisibleByPostId(@Param("postId") Long postId);

    // 목록&상세의 commentCount — 위 목록과 같은 조건으로 센다
    @Query("""
            select c.post.id as id, count(c) as cnt from Comment c
            left join c.parent p
            where c.post.id in :postIds
              and c.deletedAt is null and c.hiddenReason is null
              and (p is null or (p.deletedAt is null and p.hiddenReason is null))
            group by c.post.id
            """)
    List<IdCount> countVisibleByPostIds(@Param("postIds") Collection<Long> postIds);
}
