package com.dangdang.domain.post.dto.response;

// voteCount는 투표한 사람에게만 숫자가 온다 (U-02 → U-09)
public record PollOptionResponse(int index, String text, Long voteCount) {
}
