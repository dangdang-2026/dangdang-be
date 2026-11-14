package com.dangdang.domain.comment.repository;

import com.dangdang.domain.comment.entity.CommentReaction;
import com.dangdang.global.common.IdCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface CommentReactionRepository extends JpaRepository<CommentReaction, Long> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO comment_reactions (comment_id, profile_id, created_at)
            VALUES (:commentId, :profileId, :now)
            ON CONFLICT (comment_id, profile_id) DO NOTHING
            """)
    void addIfAbsent(@Param("commentId") Long commentId, @Param("profileId") Long profileId, @Param("now") Instant now);

    @Modifying
    @Query("delete from CommentReaction r where r.comment.id = :commentId and r.profile.id = :profileId")
    void remove(@Param("commentId") Long commentId, @Param("profileId") Long profileId);

    long countByCommentId(Long commentId);

    @Query("select r.comment.id as id, count(r) as cnt from CommentReaction r where r.comment.id in :ids group by r.comment.id")
    List<IdCount> countByCommentIds(@Param("ids") Collection<Long> commentIds);

    @Query("select r.comment.id from CommentReaction r where r.comment.id in :ids and r.profile.id = :profileId")
    Set<Long> findReactedCommentIds(@Param("ids") Collection<Long> commentIds, @Param("profileId") Long profileId);
}
