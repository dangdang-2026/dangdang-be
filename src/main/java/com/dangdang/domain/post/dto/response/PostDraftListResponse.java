package com.dangdang.domain.post.dto.response;

import java.util.List;

// 임시 글은 한 사람에 하나라 항상 다음 페이지 없음
public record PostDraftListResponse(List<PostDraftResponse> items, String nextCursor, boolean hasNext) {

    public static PostDraftListResponse of(List<PostDraftResponse> items) {
        return new PostDraftListResponse(items, null, false);
    }
}
