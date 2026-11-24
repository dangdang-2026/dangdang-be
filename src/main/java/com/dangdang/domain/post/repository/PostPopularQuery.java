package com.dangdang.domain.post.repository;

import com.dangdang.domain.comment.entity.Comment;
import com.dangdang.domain.community.entity.ProfileBlock;
import com.dangdang.domain.post.entity.Post;
import com.dangdang.domain.post.entity.PostReaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

// 인기 게시판 (U-23) — 공감 수 + 보이는 댓글 수로 DB에서 정렬하고 위에서 limit개만 가져온다
@Repository
@RequiredArgsConstructor
public class PostPopularQuery {

    private final EntityManager em;

    public List<Post> findTop(Specification<Post> filter, Long viewerProfileId, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Post> query = cb.createQuery(Post.class);
        Root<Post> root = query.from(Post.class);
        query.select(root).where(filter.toPredicate(root, query, cb));

        Expression<Long> score = cb.sum(reactionCount(query, root, cb), commentCount(query, root, cb, viewerProfileId));
        query.orderBy(cb.desc(score), cb.desc(root.<Instant>get("createdAt")), cb.desc(root.<Long>get("id")));
        return em.createQuery(query).setMaxResults(limit).getResultList();
    }

    private Subquery<Long> reactionCount(CriteriaQuery<?> query, Root<Post> post, CriteriaBuilder cb) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<PostReaction> reaction = sub.from(PostReaction.class);
        return sub.select(cb.count(reaction)).where(cb.equal(reaction.get("post"), post));
    }

    // 목록에 찍히는 commentCount(CommentRepository.countVisibleByPostIds)와 같은 조건
    private Subquery<Long> commentCount(CriteriaQuery<?> query, Root<Post> post, CriteriaBuilder cb, Long viewer) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<Comment> comment = sub.from(Comment.class);
        Join<Comment, Comment> parent = comment.join("parent", JoinType.LEFT);

        Subquery<Long> blocked = sub.subquery(Long.class);
        Root<ProfileBlock> block = blocked.from(ProfileBlock.class);
        blocked.select(block.get("id")).where(
                cb.equal(block.get("profile").get("id"), viewer),
                cb.or(cb.and(cb.equal(block.get("blockedProfile"), comment.get("profile")),
                                cb.equal(block.get("viaAnonymous"), comment.get("anonymous"))),
                        cb.and(cb.equal(block.get("blockedProfile"), parent.get("profile")),
                                cb.equal(block.get("viaAnonymous"), parent.get("anonymous")))));

        return sub.select(cb.count(comment)).where(
                cb.equal(comment.get("post"), post),
                cb.isNull(comment.get("deletedAt")),
                cb.isNull(comment.get("hiddenReason")),
                cb.or(cb.isNull(parent.get("id")),
                        cb.and(cb.isNull(parent.get("deletedAt")), cb.isNull(parent.get("hiddenReason")))),
                cb.not(cb.exists(blocked)));
    }
}
