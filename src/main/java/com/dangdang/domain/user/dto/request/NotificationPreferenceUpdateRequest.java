package com.dangdang.domain.user.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

// 모두 선택 — 안 보낸 칸은 그대로. 칸을 보냈는데 값이 null이면 400 (Nulls.FAIL)
// record로 만들면 안 보낸 칸까지 실패해서 일부러 class로 만듬
@Getter
@NoArgsConstructor
public class NotificationPreferenceUpdateRequest {

    @JsonSetter(nulls = Nulls.FAIL)
    private Boolean measurementEnabled;

    @JsonSetter(nulls = Nulls.FAIL)
    private Boolean scheduleEnabled;

    @JsonSetter(nulls = Nulls.FAIL)
    private Boolean hideContent;

    @JsonSetter(nulls = Nulls.FAIL)
    private Boolean renotifyEnabled;

    @JsonSetter(nulls = Nulls.FAIL)
    @JsonFormat(pattern = "HH:mm")
    private LocalTime fastingAlarmTime;

    @JsonSetter(nulls = Nulls.FAIL)
    @JsonFormat(pattern = "HH:mm")
    private LocalTime bedtimeAlarmTime;

    // is·get으로 시작하지 않아서 JSON 칸이 되지 않는다
    public boolean hasNoField() {
        return measurementEnabled == null && scheduleEnabled == null && hideContent == null
                && renotifyEnabled == null && fastingAlarmTime == null && bedtimeAlarmTime == null;
    }
}
