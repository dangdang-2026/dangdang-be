package com.dangdang.domain.record.controller;

import com.dangdang.domain.record.dto.response.TodayResponse;
import com.dangdang.domain.record.service.TodayService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Today", description = "오늘 화면(홈) API")
@RestController
@RequestMapping("/api/today")
@RequiredArgsConstructor
public class TodayController {

    private final TodayService todayService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 오늘 화면 조회 ]",
            description =
                    """
                    **Parameters** 없음 (오늘 = 한국 시각 00:00 ~ 다음 날 00:00)
                    **Returns** date, criteria(지금 병원 기준, 없으면 null), meals(오늘 식사, 각 식사에 items 포함), readings(오늘 혈당 + timing·aboveTarget),
                    insulinLogs(오늘 인슐린 기록 {insulinLogId, insulinType, mealSlot, units, takenAt, editedAt, createdAt, updatedAt}, 맞은 시각 순, 없으면 []),
                    nextReminder(다음 측정 알림, 없으면 null), pushReady(살아 있는 푸시 구독 여부).
                    timing은 예정 시각 ±15분 기준 EARLY·ON_TIME·LATE, 예정 시각을 모르면 null. aboveTarget은 목표가 없으면 null
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<TodayResponse>> today(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(BaseResponse.success(200, "오늘 화면 조회에 성공했습니다.",
                todayService.today(principal.userId())));
    }
}
