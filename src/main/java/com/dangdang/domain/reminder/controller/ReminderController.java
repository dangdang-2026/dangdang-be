package com.dangdang.domain.reminder.controller;

import com.dangdang.domain.reminder.dto.response.ReminderOpenResponse;
import com.dangdang.domain.reminder.dto.response.ReminderPageResponse;
import com.dangdang.domain.reminder.dto.response.ReminderSnoozeResponse;
import com.dangdang.domain.reminder.service.ReminderQueryService;
import com.dangdang.domain.reminder.service.ReminderService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.common.QueryParams;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Reminder", description = "혈당 측정 알림 API")
@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderQueryService reminderQueryService;
    private final ReminderService reminderService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 측정 알림 목록 ]",
            description =
                    """
                    **Parameters** from, to (필수, yyyy-MM-dd 한국 날짜, 양쪽 포함), page(기본 0), size(기본 20, 1~100)
                    **Returns** items, page, size, hasNext
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<ReminderPageResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        ReminderPageResponse response = reminderQueryService.list(principal.userId(),
                QueryParams.requiredDate(from, "from"),
                QueryParams.requiredDate(to, "to"),
                QueryParams.intInRange(page, 0, 0, Integer.MAX_VALUE, "page"),
                QueryParams.intInRange(size, 20, 1, 100, "size"));
        return ResponseEntity.ok(BaseResponse.success(200, "혈당 측정 알림 조회에 성공했습니다.", response));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 측정 알림 열람 ]",
            description =
                    """
                    **Parameters** id (알림 번호)
                    **Returns** id, status, openedAt. 남의 알림 REMINDER_40401, 열 수 없는 상태 REMINDER_40901
                    """)
    @PostMapping("/{id}/open")
    public ResponseEntity<BaseResponse<ReminderOpenResponse>> open(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        return ResponseEntity.ok(BaseResponse.success(200, "혈당 측정 알림 열람에 성공했습니다.",
                reminderService.open(principal.userId(), id)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 측정 알림 10분 뒤 다시 ]",
            description =
                    """
                    **Parameters** id (알림 번호), 본문 없음
                    **Returns** id, status, scheduledAt(원래 예정 시각), snoozedUntil. SENT·OPENED만, 그 외 REMINDER_40902
                    """)
    @PostMapping("/{id}/snooze")
    public ResponseEntity<BaseResponse<ReminderSnoozeResponse>> snooze(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        return ResponseEntity.ok(BaseResponse.success(200, "10분 뒤 다시 알려 드릴게요.",
                reminderService.snooze(principal.userId(), id)));
    }
}
