package com.dangdang.domain.announcement.dto.request;

import com.dangdang.domain.announcement.enums.AnnouncementKind;
import com.dangdang.domain.announcement.enums.AnnouncementSlot;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record AnnouncementCreateRequest(
        @Schema(example = "SPLASH") @NotNull AnnouncementSlot slot,
        @Schema(example = "NEWS") @NotNull AnnouncementKind kind,
        @Schema(example = "식후 혈당, 언제 재나요?") @NotBlank @Size(max = 60) String title,
        @Size(max = 500) @Pattern(regexp = "https://.*", message = "이미지는 https 주소만 돼요.") String imageUrl,
        @Schema(description = "앱 안 경로(/로 시작, //는 안 됨) 또는 https 주소만 — javascript: 같은 주소를 막는다", example = "/cards/3")
        @Size(max = 500) @Pattern(regexp = "(/(?!/)|https://).*", message = "링크는 /로 시작하는 앱 경로나 https 주소만 돼요.") String linkUrl,
        Long contentCardId,
        @Schema(description = "시작 (안 보내면 지금)") Instant activeFrom,
        @Schema(description = "끝 (안 보내면 내릴 때까지)") Instant activeTo,
        @Schema(description = "광고 심사한 사람 — AD면 필수 (AD-03)", example = "다현") @Size(max = 50) String reviewedBy) {

    @AssertTrue(message = "광고는 심사한 사람을 적어야 해요.")
    public boolean isAdReviewed() {
        return kind != AnnouncementKind.AD || (reviewedBy != null && !reviewedBy.isBlank());
    }

    @AssertTrue(message = "끝 시각은 시작보다 뒤여야 해요.")
    public boolean isPeriodValid() {
        return activeFrom == null || activeTo == null || activeTo.isAfter(activeFrom);
    }
}
