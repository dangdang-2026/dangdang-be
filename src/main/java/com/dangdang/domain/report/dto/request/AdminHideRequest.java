package com.dangdang.domain.report.dto.request;

import com.dangdang.domain.report.enums.ReportReason;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminHideRequest(
        @NotNull(message = "가리는 이유를 골라 주세요.")
        ReportReason reason,                 // 사용자 화면(U-14)에 사유별 문구로 나간다

        @Size(max = 200, message = "메모는 200자까지 적을 수 있어요.")
        String memo) {                       // 운영자끼리만 본다

    // 위급(CRISIS) 글은 가릴 글이 아니라 연락할 사람이라 가리기 사유로 못 고른다. null은 @NotNull이 따로 막음
    @JsonIgnore
    @AssertTrue(message = "위급 글은 가리기 사유로 고를 수 없어요.")
    public boolean isHideableReason() {
        return reason != ReportReason.CRISIS;
    }
}
