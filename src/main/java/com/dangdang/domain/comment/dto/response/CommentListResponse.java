package com.dangdang.domain.comment.dto.response;

import java.util.List;

public record CommentListResponse(List<CommentResponse> items, long totalCount) {
}
