package com.dangdang.domain.comment.dto.response;

import java.util.List;

public record MyCommentListResponse(List<MyCommentResponse> items, String nextCursor, boolean hasNext) {
}
