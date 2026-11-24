package com.dangdang.domain.caresite.service;

import com.dangdang.domain.caresite.infra.HospitalListClient;
import com.dangdang.domain.caresite.infra.HospitalSearchResult;
import com.dangdang.domain.caresite.repository.HospitalRepository;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

// 심평원 병원 전체 목록 -> hospitals 표
// - 1,000줄씩 받아서 쪽마다 바로 저장 (중간에 멈춰도 받은 데까지는 남음)
// - 한 쪽이 실패하면 3번까지 다시 해 봄 (심평원이 504를 자주 줌)
// - 임신성 당뇨를 볼 수 있는 종류(종합·병원·의원·보건소)만 저장. 요양병원·정신병원·치과·한방은 버림
// - 전체 수만큼 끝까지 받았을 때만: 이번에 안 나온 병원(문 닫은 곳·버리는 종류)을 지우고, 「끝까지 받음」을 남김
// - 서버가 여러 대여도 한 번에 한 대만 돌게 DB 잠금(shedlock 표, 이름 hospitalSync)을 잡고 돈다
// 전국 79,867곳(11/22) = 80쪽, 한 쪽 55초쯤 -> 한 바퀴 1시간 남짓. 하루 호출 한도(10,000) 안
@Slf4j
@Service
public class HospitalSyncService {

    static final int PAGE_SIZE = 1_000;
    private static final int MAX_PAGES = 300;          // 30만 줄. 혹시 끝이 안 나도 여기서 멈춤
    private static final int TRIES = 3;
    private static final String LOCK_NAME = "hospitalSync";
    private static final Duration LOCK_AT_MOST = Duration.ofHours(3);   // 한 바퀴 1시간 남짓. 서버가 죽어도 3시간 뒤엔 풀림

    // 저장하는 병원 종류 (심평원 clCdNm). 산모가 「다니는 병원」으로 등록할 만한 곳만
    static final Set<String> KEPT_CATEGORIES = Set.of("상급종합", "종합병원", "병원", "의원", "보건소", "보건의료원");

    private final HospitalListClient client;
    private final HospitalRepository repository;
    private final Clock clock;
    // 알림 스케줄러용 LockProvider 빈은 테스트에서 꺼져 있어서, 같은 shedlock 표를 쓰는 잠금을 여기서 직접 만든다
    private final LockProvider lockProvider;

    private final AtomicBoolean running = new AtomicBoolean(false);   // 이 서버 안에서 두 번 안 돌게

    public HospitalSyncService(HospitalListClient client, HospitalRepository repository, Clock clock,
                               JdbcTemplate jdbcTemplate) {
        this.client = client;
        this.repository = repository;
        this.clock = clock;
        this.lockProvider = new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(jdbcTemplate)
                .usingDbTime()
                .build());
    }

    // total = 심평원 전체 수, received = 받은 줄, saved = 넣은 줄, removed = 지운 줄
    // completed = 전체 수만큼 끝까지 받았는지 (이때만 지우고 「끝까지 받음」을 남김)
    public record Result(int total, int received, int saved, int removed, boolean completed) {

        static Result skipped() {
            return new Result(-1, 0, 0, 0, false);
        }
    }

    // 다른 스레드에서 시작하고 바로 돌아옴 (앱 시작·매주 일정·운영자 버튼). 이 서버에서 이미 도는 중이면 false
    // 다른 서버가 돌고 있으면 시작은 하지만 잠금을 못 잡아서 바로 끝남 (로그 「다른 서버에서 도는 중」)
    public boolean startInBackground() {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        Thread.ofVirtual().name("hospital-sync").start(() -> {
            try {
                runLocked();
            } catch (RuntimeException e) {
                log.error("[HospitalSync] 예상 못 한 오류로 멈춤", e);
            } finally {
                running.set(false);
            }
        });
        return true;
    }

    // 지금 스레드에서 끝까지 (테스트). 이미 도는 중이면 건너뜀
    public Result syncAll() {
        if (!running.compareAndSet(false, true)) {
            log.warn("[HospitalSync] 이 서버에서 이미 도는 중이라 건너뜀");
            return Result.skipped();
        }
        try {
            return runLocked();
        } finally {
            running.set(false);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    // 서버 여러 대 사이 잠금. 못 잡으면(다른 서버가 도는 중) 아무것도 안 함
    private Result runLocked() {
        // 잠금 시각은 앱 Clock이 아니라 ShedLock 시계로 (SchedulingConfig와 같은 생각: 잠금은 DB 시계, 업무 시각만 앱 Clock)
        // 앱 Clock(테스트에선 일부러 옮긴 시계)으로 넣으면 풀어도 그 시각까지 잠긴 채로 남는다
        Optional<SimpleLock> lock = lockProvider.lock(
                new LockConfiguration(ClockProvider.now(), LOCK_NAME, LOCK_AT_MOST, Duration.ZERO));
        if (lock.isEmpty()) {
            log.warn("[HospitalSync] 다른 서버에서 도는 중이라 건너뜀");
            return Result.skipped();
        }
        try {
            return runOnce();
        } finally {
            lock.get().unlock();
        }
    }

    private Result runOnce() {
        Instant startedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);   // DB(마이크로초)와 자릿수 맞춤
        log.info("[HospitalSync] 시작");
        int total = -1;
        int received = 0;
        int saved = 0;
        for (int page = 1; page <= MAX_PAGES; page++) {
            HospitalSearchResult result = fetchWithRetry(page);
            if (result == null) {
                log.error("[HospitalSync] {}쪽에서 멈춤. 받은 {}줄은 저장됨, 옛 병원은 안 지움", page, saved);
                return new Result(total, received, saved, 0, false);
            }
            if (total < 0) {
                total = result.totalCount();
            }
            if (result.hospitals().isEmpty()) {
                break;   // 전체 수보다 일찍 빈 쪽이 오면 아래에서 「끝까지 못 받음」으로 처리
            }
            received += result.hospitals().size();
            List<HospitalSearchResult.Hospital> kept = result.hospitals().stream()
                    .filter(h -> KEPT_CATEGORIES.contains(h.category()))
                    .toList();
            saved += repository.upsert(kept, startedAt);
            if (page % 10 == 0) {
                log.info("[HospitalSync] {}쪽까지 {}/{}줄", page, received, total);
            }
            if (received >= total) {
                break;
            }
        }
        // 전체 수만큼 다 받았을 때만 지운다. 하나라도 덜 받았으면 아직 있는 병원까지 지울 수 있어서 안 지움
        boolean completed = total > 0 && received >= total;
        if (!completed) {
            log.warn("[HospitalSync] 끝까지 못 받음 전체={} 받음={} 저장={} → 옛 병원은 안 지움", total, received, saved);
            return new Result(total, received, saved, 0, false);
        }
        int removed = repository.deleteSyncedBefore(startedAt);
        repository.markCompleted(clock.instant(), total, saved, removed);
        log.info("[HospitalSync] 끝 전체={} 받음={} 저장={} 지움={}", total, received, saved, removed);
        return new Result(total, received, saved, removed, true);
    }

    // 실패하면 1초, 2초 쉬고 다시. 3번 다 실패하면 null
    private HospitalSearchResult fetchWithRetry(int page) {
        for (int attempt = 1; attempt <= TRIES; attempt++) {
            try {
                return client.fetchPage(page, PAGE_SIZE);
            } catch (RuntimeException e) {
                log.warn("[HospitalSync] {}쪽 {}번째 실패 - {}", page, attempt, e.getMessage());
                if (attempt < TRIES) {
                    sleep(attempt * 1_000L);
                }
            }
        }
        return null;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
