package com.dangdang.domain.post.dto.response;

import com.dangdang.domain.post.entity.PostDraft;
import com.dangdang.domain.post.enums.PostCategory;

import java.time.Instant;
import java.util.List;

public record PostDraftResponse(
        String id,
        PostCategory category,
        String title,
        String content,
        boolean isAnonymous,
        List<String> poll,
        Instant updatedAt) {

    public static PostDraftResponse from(PostDraft draft) {
        return new PostDraftResponse(
                String.valueOf(draft.getId()), draft.getCategory(), draft.getTitle(), draft.getContent(),
                draft.isAnonymous(), draft.getPoll(), draft.getUpdatedAt());
    }
}
