package com.dangdang.domain.post.dto.request;

import com.dangdang.domain.post.enums.PostCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PostCreateRequest(
        @NotNull(message = "게시판을 골라 주세요.")
        PostCategory category,

        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = 100, message = "제목은 100자까지 적을 수 있어요.")
        String title,

        @NotBlank(message = "내용을 입력해 주세요.")
        @Size(max = 5000, message = "내용은 5000자까지 적을 수 있어요.")
        String content,

        Boolean isAnonymous,

        @Size(min = 2, max = 5, message = "투표 항목은 2~5개로 만들어 주세요.")
        List<@NotBlank(message = "빈 투표 항목이 있어요.")
        @Size(max = 50, message = "투표 항목은 50자까지 적을 수 있어요.") String> poll,

        // U-15 「그대로 올리기」 · U-30 「그래도 글 올리기」: 올리기 전 확인(precheck)의 issues[].kind 중 사용자가 보고도 올린 것
        // 서버는 같은 사람이 같은 내용으로 30분 안에 확인했을 때 실제로 걸린 종류만 인정하고 나머지는 조용히 버린다 (글은 그대로 올라감)
        @Schema(description = "올리기 전 확인에서 안내를 보고도 그대로 올린 kind (선택, 최대 4개). "
                + "같은 내용으로 30분 안에 확인해서 실제로 걸린 것만 운영자 처리함에 들어가고, 아닌 건 조용히 버린다. "
                + "DOSE → MEDICAL_ADVICE, URGENT_MEDICAL · MIND · LOW_GLUCOSE → CRISIS",
                example = "[\"DOSE\"]")
        @Size(max = 4, message = "확인 종류를 다시 보내 주세요.")
        List<@NotNull(message = "확인 종류를 다시 보내 주세요.")
        @Pattern(regexp = "[A-Z_]{1,30}", message = "확인 종류를 다시 보내 주세요.") String> postedDespite) {
}
