package com.dangdang.domain.community.service;

import com.dangdang.domain.community.entity.CommunityProfile;
import com.dangdang.domain.pregnancy.entity.Pregnancy;
import com.dangdang.domain.pregnancy.enums.PregnancyState;
import com.dangdang.domain.pregnancy.repository.PregnancyRepository;
import com.dangdang.global.config.ClockConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

// 글·댓글 글쓴이 옆 주수 배지 (U-02 · U-21 · U-02b). 배지를 켠 사람만. 익명 글·댓글에는 AuthorResponse가 안 붙인다
@Component
@RequiredArgsConstructor
public class WeekBadges {

    private static final int PREGNANCY_DAYS = 280;   // 예정일 기준 40주 (MeResponse와 같은 셈)

    private final PregnancyRepository pregnancyRepository;
    private final Clock clock;

    // profileId → 「임신 27주」·「산후 3개월」. 배지를 껐거나 계산할 수 없으면 map에 없다
    // 글쓴이가 몇 명이어도 임신 표는 한 번만 읽는다. 탈퇴한 글쓴이(null)는 건너뛴다
    public Map<Long, String> of(Collection<CommunityProfile> profiles) {
        Map<Long, Long> profileOfUser = new HashMap<>();
        for (CommunityProfile p : profiles) {
            if (p != null && p.isShowWeekBadge()) {
                profileOfUser.put(p.getUser().getId(), p.getId());   // user는 지연 로딩이어도 id는 쿼리 없이 나온다
            }
        }
        if (profileOfUser.isEmpty()) {
            return Map.of();
        }
        LocalDate today = clock.instant().atZone(ClockConfig.KST).toLocalDate();   // 앱 시계, 한국 날짜
        Map<Long, String> badges = new HashMap<>();
        for (Pregnancy pregnancy : pregnancyRepository.findByUserIdInAndStateNot(profileOfUser.keySet(),
                PregnancyState.ENDED)) {
            String badge = badgeOf(pregnancy, today);
            if (badge != null) {
                badges.put(profileOfUser.get(pregnancy.getUser().getId()), badge);
            }
        }
        return badges;
    }

    static String badgeOf(Pregnancy p, LocalDate today) {
        if (p.getState() == PregnancyState.PAUSED) {
            return null;   // 「다른 상황이에요」(E-03) — 무슨 일인지 안 묻는 상태라 주수도 산후 개월도 안 보여 준다
        }
        if (p.getState() == PregnancyState.POSTPARTUM) {
            if (p.getDeliveryDate() == null) {
                return null;
            }
            long months = ChronoUnit.MONTHS.between(p.getDeliveryDate(), today);   // 출산일부터 꽉 찬 달
            return months < 1 ? "산후 1개월 미만" : "산후 " + months + "개월";
        }
        if (p.getDueDate() == null) {
            return null;
        }
        long elapsed = PREGNANCY_DAYS - ChronoUnit.DAYS.between(today, p.getDueDate());
        if (elapsed < 0) {
            return null;   // 예정일이 280일보다 뒤 — 잘못 적은 날짜 (MeResponse와 같게 안 보여 줌)
        }
        return "임신 " + (elapsed / 7) + "주";   // 내림 (/api/me 「26주 3일」이면 「임신 26주」)
    }
}
