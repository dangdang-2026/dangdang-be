package com.dangdang.domain.caresite.scheduler;

import com.dangdang.domain.caresite.repository.HospitalRepository;
import com.dangdang.domain.caresite.service.HospitalSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 병원 목록 적재 일정. hira.sync-enabled=true 인 서버에서만 생김 (로컬·테스트는 안 생김)
//  - 앱이 떴는데 끝까지 받은 적이 없으면: 바로 한 번 (뒤에서, 앱 뜨는 건 안 막음)
//  - 둘 다 가상 스레드로 넘기고 바로 돌아온다 → 스케줄러 스레드(2개, 알림·SSE ping용)를 1시간씩 잡지 않음
//  - 서버 여러 대 사이 겹침은 HospitalSyncService가 DB 잠금으로 막음
//  - 매주 일요일 04:30: 다시 받아서 고치기 (03:30 DB 백업, 월 04:00 음식 동기화와 안 겹치게)
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hira.sync-enabled", havingValue = "true")
public class HospitalSyncScheduler {

    private final HospitalSyncService hospitalSyncService;
    private final HospitalRepository hospitalRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void loadIfEmpty() {
        if (!hospitalRepository.hasCompletedSync()) {
            log.info("[HospitalSync] 끝까지 받은 적이 없어서 처음 적재를 시작함");
            hospitalSyncService.startInBackground();
        }
    }

    @Scheduled(cron = "0 30 4 * * SUN", zone = "Asia/Seoul")
    public void weekly() {
        hospitalSyncService.startInBackground();
    }
}
