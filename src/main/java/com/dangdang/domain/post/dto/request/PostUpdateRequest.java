package com.dangdang.domain.post.dto.request;

import com.dangdang.domain.post.enums.PostCategory;
import jakarta.validation.constraints.Size;

// 보낸 칸만 바꾼다. 투표(poll)는 일부러 없다 — 올린 뒤 고칠 수 없음 (U-10)
public record PostUpdateRequest(
        PostCategory category,

        @Size(max = 100, message = "제목은 100자까지 적을 수 있어요.")
        String title,

        @Size(max = 5000, message = "내용은 5000자까지 적을 수 있어요.")
        String content) {
}
