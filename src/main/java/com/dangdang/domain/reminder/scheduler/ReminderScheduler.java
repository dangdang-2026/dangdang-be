package com.dangdang.domain.reminder.scheduler;

import com.dangdang.domain.postpartum.service.PostpartumTestNotifier;
import com.dangdang.domain.reminder.service.DailyReminderPlanner;
import com.dangdang.domain.reminder.service.ReminderDispatchService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

// 서버가 여러 대여도 한 바퀴는 한 대만 돈다 (ShedLock — SchedulingConfig)
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    public static final String LOCK_NAME = "reminderScheduler";

    private final ReminderDispatchService reminderDispatchService;
    private final DailyReminderPlanner dailyReminderPlanner;
    private final PostpartumTestNotifier postpartumTestNotifier;

    // 앞 바퀴가 끝나고 30초 뒤에 다음 바퀴 (겹치지 않는다). 테스트는 이 메서드를 직접 부른다
    // 잠금: 다른 서버가 돌고 있으면 이번 바퀴는 건너뛴다. 최소 20초는 잡고 있어서 다른 서버가 바로 또 돌지 않고,
    //       돌던 서버가 죽어도 5분 뒤엔 풀린다
    @Scheduled(initialDelay = 30, fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    @SchedulerLock(name = LOCK_NAME, lockAtLeastFor = "PT20S", lockAtMostFor = "PT5M")
    public void run() {
        dailyReminderPlanner.planDue();          //공복과 자기 전 알림을 먼저 만들어야 같은 바퀴에 바로 보낸다
        reminderDispatchService.dispatchDue();
        reminderDispatchService.renotifyDue();
        postpartumTestNotifier.notifyDue();      // 산후 검사 전날 저녁(한국 7~10시)·당일 아침(7시부터) 알림
    }
}
