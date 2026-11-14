package com.dangdang.domain.post.dto.response;

// 글 공감·댓글 공감이 같이 쓴다
public record ReactionResponse(boolean reacted, long reactionCount) {
}
