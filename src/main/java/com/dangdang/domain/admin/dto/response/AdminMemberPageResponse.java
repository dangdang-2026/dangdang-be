package com.dangdang.domain.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

// 운영자 회원 목록 한 쪽. 건강정보(혈당·식사·임신)는 넣지 않는다
public record AdminMemberPageResponse(
        @Schema(description = "전체 탭 숫자 (운영자 계정 빼고)") long totalCount,
        @Schema(description = "제한 중 탭 숫자") long restrictedCount,
        List<Item> items, int page, int size, boolean hasNext) {

    public record Item(
            Long userId,
            @Schema(description = "커뮤니티 닉네임. 프로필이 없으면 null") String nickname,
            @Schema(description = "앱에서 부르는 이름") String displayName,
            @Schema(description = "앞 두 글자만 보이는 이메일. 카카오 가입이면 null", example = "dd***@gmail.com") String email,
            @Schema(description = "EMAIL · KAKAO") String authProvider,
            Instant joinedAt,
            @Schema(description = "지금 이용 제한 중인지") boolean restricted,
            @Schema(description = "제한 중일 때 풀리는 시각. 풀 때까지면 null") Instant restrictionEndsAt,
            @Schema(description = "지우지 않은 글 수") long postCount,
            @Schema(description = "내 글·댓글이 받은 신고 수 (처리한 것 포함)") long reportedCount) {
    }
}
