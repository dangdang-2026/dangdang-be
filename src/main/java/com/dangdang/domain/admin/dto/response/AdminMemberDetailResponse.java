package com.dangdang.domain.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

// 운영자 회원 상세. 커뮤니티 활동과 신고·제한 이력만 (건강정보는 넣지 않는다)
public record AdminMemberDetailResponse(
        Long userId,
        @Schema(description = "커뮤니티 닉네임. 프로필이 없으면 null") String nickname,
        String displayName,
        @Schema(description = "앞 두 글자만 보이는 이메일. 카카오 가입이면 null") String email,
        @Schema(description = "EMAIL · KAKAO") String authProvider,
        @Schema(description = "이메일 가입 뒤 카카오를 이었는지") boolean kakaoLinked,
        Instant joinedAt,
        @Schema(description = "지우지 않은 글 수") long postCount,
        @Schema(description = "지우지 않은 댓글 수") long commentCount,
        @Schema(description = "운영자가 가린 글·댓글 수") long hiddenCount,
        @Schema(description = "내 글·댓글이 받은 신고 수 (처리한 것 포함)") long reportedCount,
        @Schema(description = "지금 걸린 제한. 없으면 null") Sanction activeSanction,
        @Schema(description = "제한 이력 최근 순") List<Sanction> sanctions,
        @Schema(description = "받은 신고 최근 20건") List<Report> reports) {

    public record Sanction(
            @Schema(description = "제한 번호 — 풀기(POST /api/admin/sanctions/{id}/lift)에 쓴다") Long id,
            @Schema(description = "신고 사유와 같은 말", example = "AD_SALES") String reason,
            Instant startsAt,
            @Schema(description = "null = 운영자가 풀 때까지") Instant endsAt,
            Instant liftedAt,
            @Schema(description = "제한을 건 운영자 이름. 계정이 지워졌으면 null") String createdByName) {
    }

    public record Report(
            Long reportId,
            @Schema(description = "POST · COMMENT") String targetType,
            Long targetId,
            @Schema(description = "글 제목 또는 댓글 앞부분 40자") String targetLabel,
            @Schema(description = "MEDICAL_ADVICE · NUMBER_COMPARE · AD_SALES · PRIVACY · ABUSE · CRISIS · MISINFO · ETC") String reason,
            @Schema(description = "RECEIVED · HIDDEN · DISMISSED") String status,
            Instant createdAt) {
    }
}
