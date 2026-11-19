package com.dangdang.domain.report.dto.request;

import com.dangdang.domain.report.enums.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminHideRequest(
        @NotNull(message = "가리는 이유를 골라 주세요.")
        ReportReason reason,                 // 사용자 화면(U-14)에 사유별 문구로 나간다

        @Size(max = 200, message = "메모는 200자까지 적을 수 있어요.")
        String memo) {                       // 운영자끼리만 본다
}
