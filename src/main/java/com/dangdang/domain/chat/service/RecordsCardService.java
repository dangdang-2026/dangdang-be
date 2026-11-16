package com.dangdang.domain.chat.service;

import com.dangdang.domain.chat.entity.ChatPayload;
import com.dangdang.domain.chat.infra.ChatClient;
import com.dangdang.domain.chat.repository.ChatRecordsQuery;
import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.reading.entity.Reading;
import com.dangdang.global.config.ClockConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

// Q-03 내 기록 카드 — 숫자·횟수는 여기서만 센다. 「괜찮다/위험하다」 같은 해석은 넣지 않는다 (ASK-03)
@Service
@RequiredArgsConstructor
public class RecordsCardService {

    static final int MAX_DAYS = 31;      // 한 번에 보여 줄 기간
    static final int MAX_ITEMS = 100;    // 카드에 담을 기록 수

    private final ChatRecordsQuery chatRecordsQuery;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ChatPayload.RecordsCard build(Long userId, ChatClient.Records ask) {
        LocalDate today = LocalDate.now(clock.withZone(ClockConfig.KST));
        LocalDate to = ask.to() == null || ask.to().isAfter(today) ? today : ask.to();
        LocalDate from = ask.from() == null ? to.minusDays(6) : ask.from();     // 기간을 못 정했으면 최근 7일
        if (from.isAfter(to)) {
            LocalDate t = from; from = to; to = t;
        }
        if (from.isBefore(to.minusDays(MAX_DAYS - 1))) {
            from = to.minusDays(MAX_DAYS - 1);
        }
        MeasureSlot slot = parseSlot(ask.slot());
        List<Reading> readings = chatRecordsQuery.readings(userId,
                from.atStartOfDay(ClockConfig.KST).toInstant(),
                to.plusDays(1).atStartOfDay(ClockConfig.KST).toInstant(),
                slot, MAX_ITEMS);
        List<ChatPayload.Item> items = readings.stream()
                .map(r -> new ChatPayload.Item(r.getMeasuredAt(),
                        r.getSlot() == null ? null : r.getSlot().name(),
                        r.getReadingType().name(), r.getValue()))
                .toList();
        return new ChatPayload.RecordsCard(from, to, slot == null ? null : slot.name(), items.size(), items);
    }

    private MeasureSlot parseSlot(String slot) {
        if (slot == null || slot.isBlank()) {
            return null;
        }
        try {
            return MeasureSlot.valueOf(slot);
        } catch (IllegalArgumentException e) {
            return null;   // 모르는 이름이면 전체
        }
    }
}
