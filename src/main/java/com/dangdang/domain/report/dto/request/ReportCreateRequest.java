package com.dangdang.domain.report.dto.request;

import com.dangdang.domain.report.enums.ReportReason;
import com.dangdang.domain.report.enums.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReportCreateRequest(
        @NotNull(message = "무엇을 신고할지 알려 주세요.")
        ReportTargetType targetType,

        @NotNull(message = "무엇을 신고할지 알려 주세요.")
        @Positive(message = "신고 대상 번호를 확인해 주세요.")
        Long targetId,

        @NotNull(message = "신고 이유를 골라 주세요.")
        ReportReason reason,

        @Size(max = 200, message = "내용은 200자까지 적을 수 있어요.")
        String detail) {       // ETC일 때만 저장한다
}
