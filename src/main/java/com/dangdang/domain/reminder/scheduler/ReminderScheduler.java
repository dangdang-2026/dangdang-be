package com.dangdang.domain.reminder.scheduler;

import com.dangdang.domain.postpartum.service.PostpartumTestNotifier;
import com.dangdang.domain.reminder.service.DailyReminderPlanner;
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
    private final DailyReminderPlanner dailyReminderPlanner;
    private final PostpartumTestNotifier postpartumTestNotifier;

    // 앞 바퀴가 끝나고 30초 뒤에 다음 바퀴 (겹치지 않는다). 테스트는 이 메서드를 직접 부른다
    @Scheduled(initialDelay = 30, fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    public void run() {
        dailyReminderPlanner.planDue();          //공복과 자기 전 알림을 먼저 만들어야 같은 바퀴에 바로 보낸다
        reminderDispatchService.dispatchDue();
        reminderDispatchService.renotifyDue();
        postpartumTestNotifier.notifyDue();      // 산후 검사 전날,당일 알림 (오전 9시부터)
    }
}
