package com.dangdang.domain.reminder.scheduler;

import com.dangdang.domain.reminder.service.ReminderDispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

// 서버 1대 기준이라 잠금(ShedLock)은 아직 없음 — 서버가 2대가 되면 넣기
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final ReminderDispatchService reminderDispatchService;

    // 앞 바퀴가 끝나고 30초 뒤에 다음 바퀴 (겹치지 않는다). 테스트는 이 메서드를 직접 부른다
    @Scheduled(initialDelay = 30, fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    public void run() {
        reminderDispatchService.dispatchDue();
        reminderDispatchService.renotifyDue();
    }
}
