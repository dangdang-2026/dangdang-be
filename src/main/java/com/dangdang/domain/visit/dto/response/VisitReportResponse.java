package com.dangdang.domain.visit.dto.response;

import com.dangdang.domain.criteria.entity.CriteriaVersion;
import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.reading.entity.Reading;
import com.dangdang.domain.reading.enums.MeterLimit;
import com.dangdang.domain.reading.enums.ReadingTiming;
import com.dangdang.domain.reading.enums.ReadingType;
import com.dangdang.domain.reading.service.ReadingEvaluator;
import com.dangdang.domain.visit.enums.ReportKind;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 진료 리포트 한 장 (C-06). 볼 때마다 다시 계산함
// 해석·권고·평균·그래프 없이 횟수와 원값만 담음 (「해석이나 권고 없이 기록만 담았어요」)
public record VisitReportResponse(
        ReportKind kind,
        LocalDate from,                      // 기간 시작 (포함)
        LocalDate to,                        // 기간 끝 (포함)
        Long visitId,                        // 진료용이면 그 일정, 기간 직접이면 null
        LocalDate visitDate,                 // 진료용이면 진료 날짜 (한국 날짜), 아니면 null
        CareSite careSite,                   // 보여 줄 병원 (없거나 지운 병원이면 null)
        String displayName,                  // 「윤지수 님 혈당 기록」. 이름 가리기는 화면에서 함
        PregnancyWeeks pregnancyWeeks,       // 기간 시작·끝의 임신 주수 (예정일이 없으면 null)
        List<Criteria> criteria,             // 기간 안에 적용된 병원 기준 (오래된 순, 바뀌었으면 여러 개)
        Summary summary,
        List<SlotCount> slots,               // 잴 때별 목표 밖 / 측정 / 빈칸
        List<Row> readings,                  // 원값 표 (잰 시각 오래된 순)
        List<MissedCell> missedCells,        // 원값 표의 「–」 = 빈칸 (날짜·잴 때, 알림 시각 순)
        Long questionCount) {                // 진료용이면 물어볼 것 수, 기간 직접이면 null

    public record CareSite(Long careSiteId, String name) {
    }

    public record PregnancyWeeks(Integer from, Integer to) {   // 그날 주수를 모르면(예정일이 280일보다 뒤) null
    }

    public record Criteria(
            Long criteriaVersionId,
            LocalDate effectiveFrom,
            List<MeasureSlot> measureSlots,
            Integer postMealMinutes,
            Integer fastingTarget,           // 미만
            Integer postMealTarget,          // 미만
            String sourceNote) {

        public static Criteria from(CriteriaVersion c) {
            return new Criteria(c.getId(), c.getEffectiveFrom(), c.getMeasureSlots(), c.getPostMealMinutes(),
                    c.getFastingTarget(), c.getPostMealTarget(), c.getSourceNote());
        }
    }

    public record Summary(
            int measured,                    // 측정
            long missed,                     // 빈칸 (알림이 갔는데 안 잰 측정, 이번 끼니 건너뛴 것 포함) = slots[].missed 합 = missedCells 개수
            int enteredLate,                 // 나중 입력 (잰 뒤 30분 넘게 지나서 적음)
            int below70,                     // 70 미만 (LO도 여기)
            int above200,                    // 200 이상 (C-06 「200 이상 없음」, 70 미만과 짝. HI도 여기)
            int outOfTarget,                 // 목표 밖 (기록마다 그때 기준으로)
            Double inTargetRate,             // 목표 안 비율 0~1 (목표와 비교한 기록이 없으면 null)
            boolean compared) {              // 목표와 비교한 기록이 하나라도 있는지
    }

    // slot이 null이면 잴 때를 고르지 않은 기록 묶음 (빈칸은 항상 0)
    // 기록이 있거나 빈칸이 있는 잴 때만 나옴 (기록 0개 · 빈칸 1개인 「아침 식후 0 / 0 · 빈칸 1」도 나옴)
    public record SlotCount(MeasureSlot slot, int measured, int outOfTarget, int missed) {
    }

    public record Row(
            Long readingId,
            Instant measuredAt,
            ReadingType readingType,
            MeasureSlot slot,
            int value,                       // HI·LO면 혈당계 끝값 600·20
            MeterLimit meterLimit,           // HI·LO로 적었으면 그 글자. 숫자면 null (G-11)
            Boolean aboveTarget,             // 그때 목표가 없었으면 null
            boolean enteredLate,             // 30분 넘게 늦게 「적었는지」
            Instant expectedAt,              // 재야 할 시각 (식후가 아니거나 모르면 null)
            ReadingTiming timing) {          // 늦게 「쟀는지」 EARLY·ON_TIME·LATE (±15분, H-05). expectedAt이 null이면 null

        public static Row of(Reading r, Boolean aboveTarget, Instant expectedAt) {
            return new Row(r.getId(), r.getMeasuredAt(), r.getReadingType(), r.getSlot(), r.getValue(),
                    r.getMeterLimit(), aboveTarget, r.isEnteredLate(),
                    expectedAt, ReadingEvaluator.timing(r.getMeasuredAt(), expectedAt));
        }
    }

    // 빈칸 한 칸 = 그날(한국 날짜) 그 잴 때 알림이 갔는데 안 잼
    public record MissedCell(LocalDate date, MeasureSlot slot) {
    }
}
