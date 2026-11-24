package com.dangdang.domain.user.service;

import com.dangdang.domain.user.dto.response.ConsentResponse;
import com.dangdang.domain.user.entity.ConsentEvent;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.enums.ConsentType;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.ConsentEventRepository;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 건강정보(민감정보) 동의 철회 (M-07b). 계정은 두고 건강정보만 지체 없이 파기한다 (처리방침 「철회하면 지체 없이 파기」)
// 탈퇴는 users 한 줄을 지워 DB CASCADE에 맡기지만, 여기서는 계정을 남기므로 표마다 직접 지운다
// deleted_at 조건을 일부러 안 건다 — 지운 기록(소프트 삭제된 줄)도 파기 대상
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveConsentWithdrawalService {

    private record Step(String table, String sql) {}

    // 순서가 중요하다 — CASCADE가 없는 외래키가 있어서
    //   reminders.meal_id → meals : 알림을 식사보다 먼저
    //   readings.criteria_version_id → criteria_versions : 혈당을 기준보다 먼저
    // meal_items는 meals를, visit_questions·visit_notes는 visits를 지우면 DB가 같이 지운다 (ON DELETE CASCADE)
    // care_sites는 기준·식사 안내·진료·리포트가 가리키니 맨 끝
    private static final List<Step> STEPS = List.of(
            // 알림함: 건강 알림 5종만. MARKETING(광고 동의 결과)·COMMUNITY_HIDDEN(가림 알림)처럼 건강이 아닌 종류는 남긴다
            // 새 종류를 만들면 여기 넣을지 정한다
            new Step("notifications", "DELETE FROM notifications WHERE user_id = ?"
                    + " AND type IN ('MEASURE', 'VISIT_BEFORE', 'VISIT_TODAY', 'VISIT_NOTE', 'POSTPARTUM')"),
            new Step("reminders", "DELETE FROM reminders WHERE user_id = ?"),
            new Step("readings", "DELETE FROM readings WHERE user_id = ?"),
            new Step("meals", "DELETE FROM meals WHERE user_id = ?"),
            new Step("criteria_versions", "DELETE FROM criteria_versions WHERE user_id = ?"),
            new Step("saved_reports", "DELETE FROM saved_reports WHERE user_id = ?"),
            new Step("visits", "DELETE FROM visits WHERE user_id = ?"),
            new Step("meal_plans", "DELETE FROM meal_plans WHERE user_id = ?"),
            new Step("postpartum_tests", "DELETE FROM postpartum_tests WHERE user_id = ?"),
            new Step("pregnancies", "DELETE FROM pregnancies WHERE user_id = ?"),
            new Step("chat_messages", "DELETE FROM chat_messages WHERE user_id = ?"),
            new Step("care_sites", "DELETE FROM care_sites WHERE user_id = ?"));

    private final UserRepository userRepository;
    private final ConsentEventRepository consentEventRepository;
    private final ConsentService consentService;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Transactional
    public ConsentResponse withdraw(Long userId) {
        // ① 이 사람 줄을 잠근다 — 두 번 눌러도(동시에 와도) 앞 요청이 끝날 때까지 기다렸다가 차례로
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        Instant now = clock.instant();
        // 철회 이력을 남길지는 「버전 상관없이 마지막 줄이 동의인지」로 본다 — 예전 버전 동의도 철회 기록은 남아야 한다
        boolean wasGranted = isLatestGranted(userId, ConsentType.SENSITIVE);

        // ② 건강정보 파기. 이미 철회한 상태여도 다시 돈다 (그사이 옛 화면에서 저장된 기록이 있을 수 있어서, 지울 게 없으면 0)
        Map<String, Integer> erased = new LinkedHashMap<>();
        for (Step step : STEPS) {
            erased.put(step.table(), jdbc.update(step.sql(), userId));
        }

        // ③ 측정 알림 끄기 — 기준·알림을 지웠으니 어차피 안 가지만, 다시 동의했을 때 모르게 울리지 않게 (M-19에서 다시 켠다)
        jdbc.update("UPDATE notification_preferences SET measurement_enabled = false, updated_at = ? WHERE user_id = ?",
                Timestamp.from(now), userId);

        // ④ 동의 이력: 덮어쓰지 않고 철회도 한 줄로 쌓는다. 이미 철회한 상태면 또 쌓지 않음 (두 번 눌러도 이력은 한 줄)
        if (wasGranted) {
            List<ConsentEvent> events = new ArrayList<>();
            events.add(ConsentEvent.of(user, ConsentType.SENSITIVE, ConsentType.SENSITIVE.getVersion(), false, now));
            // 「AI가 내 기록을 보고 답해요」는 건강정보 동의가 있어야 의미가 있어서 같이 끈다 (다시 동의할 때 따로 켬)
            if (isLatestGranted(userId, ConsentType.AI_CHAT_RECORDS)) {
                events.add(ConsentEvent.of(user, ConsentType.AI_CHAT_RECORDS,
                        ConsentType.AI_CHAT_RECORDS.getVersion(), false, now));
            }
            consentEventRepository.saveAll(events);
        }
        // 숫자만 남긴다 (내용은 로그에 안 남김)
        log.info("[동의 철회] 건강정보 파기 - 사용자: {}, 지운 줄: {}", userId, erased);
        return consentService.getStatus(userId);   // completed=false → 프론트가 바로 A-01로
    }

    private boolean isLatestGranted(Long userId, ConsentType type) {
        return consentService.findLatest(userId, type).map(ConsentEvent::isGranted).orElse(false);
    }
}
