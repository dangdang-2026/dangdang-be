package com.dangdang.domain.comment.service;

import com.dangdang.domain.comment.entity.Comment;

import java.util.List;
import java.util.Map;

// 베스트 댓글 규칙 한 곳 (U-02b 「Best」 · U-23). CommentRepository.findBestComments도 같은 규칙
public final class BestComment {

    public static final int MIN_REACTIONS = 3;   // 공감 3개 이상일 때만 (댓글 두세 개짜리 글에 공감 1개로 Best가 붙지 않게)

    private BestComment() {
    }

    // 보이는 댓글 중 답글이 아닌 것에서 공감이 가장 많은 것. 같으면 먼저 단 것(같은 시각이면 id 작은 것)
    // comments는 오래된 순(createdAt, id)이라 「더 클 때만」 바꾸면 같은 수에서는 앞의 것이 남는다. 없으면 null
    public static Long pick(List<Comment> comments, Map<Long, Long> reactionCounts) {
        Long best = null;
        long bestCount = MIN_REACTIONS - 1;
        for (Comment c : comments) {
            long n = reactionCounts.getOrDefault(c.getId(), 0L);
            if (!c.isReply() && n > bestCount) {
                best = c.getId();
                bestCount = n;
            }
        }
        return best;
    }
}
