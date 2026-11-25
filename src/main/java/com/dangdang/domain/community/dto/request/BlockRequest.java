package com.dangdang.domain.community.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;

import java.util.Objects;
import java.util.stream.Stream;

// 안 볼 사람: 셋 중 하나만. 익명 글·댓글은 profileId가 안 내려가니 글·댓글 번호로 보낸다 (U-20 · U-13 · 댓글 ⋯)
public record BlockRequest(
        @Schema(description = "이름이 보이는 글쓴이의 프로필 번호", example = "102")
        @Positive(message = "프로필 번호를 확인해 주세요.")
        Long blockedProfileId,         // JSON은 "102"처럼 글자로 와도 된다

        @Schema(description = "이 글을 쓴 사람 (익명 글이어도 된다)", example = "301")
        @Positive(message = "글 번호를 확인해 주세요.")
        Long postId,

        @Schema(description = "이 댓글을 쓴 사람 (익명 댓글이어도 된다)", example = "77")
        @Positive(message = "댓글 번호를 확인해 주세요.")
        Long commentId) {

    @JsonIgnore
    @AssertTrue(message = "안 볼 사람을 하나만 골라 주세요.")
    public boolean isExactlyOneTarget() {
        return Stream.of(blockedProfileId, postId, commentId).filter(Objects::nonNull).count() == 1;
    }
}
