package com.dangdang.domain.post.repository;

import com.dangdang.domain.community.entity.ProfileBlock;
import com.dangdang.domain.post.entity.Post;
import com.dangdang.domain.post.enums.PostCategory;
import com.dangdang.global.common.TimeCursor;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// 목록 조건을 있는 것만 골라 조립한다
public final class PostSpecifications {

    private PostSpecifications() {
    }

    public static Specification<Post> feed(PostCategory category, String q, TimeCursor cursor, Long myProfileId) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isNull(root.get("deletedAt")));      // 지운 글 제외
            where.add(cb.isNull(root.get("hiddenReason")));   // 가려진 글 제외
            if (myProfileId != null) {                        // 내가 안 보기 한 사람의 글 제외
                Subquery<Long> blocked = query.subquery(Long.class);
                Root<ProfileBlock> block = blocked.from(ProfileBlock.class);
                blocked.select(block.get("id")).where(
                        cb.equal(block.get("profile").get("id"), myProfileId),
                        cb.equal(block.get("blockedProfile").get("id"), root.get("profile").get("id")));
                where.add(cb.not(cb.exists(blocked)));
            }
            if (category != null) {
                where.add(cb.equal(root.get("category"), category));
            }
            if (q != null) {
                String pattern = "%" + escape(q.toLowerCase(Locale.ROOT)) + "%";
                where.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("content")), pattern, '\\')));
            }
            if (cursor != null) {                             // 지난 페이지 마지막 글보다 뒤의 것만
                where.add(cb.or(
                        cb.lessThan(root.<java.time.Instant>get("createdAt"), cursor.at()),
                        cb.and(cb.equal(root.get("createdAt"), cursor.at()),
                                cb.lessThan(root.<Long>get("id"), cursor.id()))));
            }
            return cb.and(where.toArray(new Predicate[0]));
        };
    }

    // 검색어의 %, _ 를 글자 그대로 찾게 한다
    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
