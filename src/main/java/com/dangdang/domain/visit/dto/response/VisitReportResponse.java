package com.dangdang.domain.visit.dto.response;

import com.dangdang.domain.criteria.entity.CriteriaVersion;
import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.insulin.entity.InsulinLog;
import com.dangdang.domain.insulin.enums.InsulinType;
import com.dangdang.domain.meal.enums.MealSlot;
import com.dangdang.domain.reading.entity.Reading;
import com.dangdang.domain.reading.enums.ReadingType;
import com.dangdang.domain.visit.enums.ReportKind;

import java.math.BigDecimal;
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
        List<SlotCount> slots,               // 잴 때별 목표 밖 / 측정
        List<Row> readings,                  // 원값 표 (잰 시각 오래된 순)
        List<InsulinRow> insulinLogs,        // 원값 표 · 인슐린 (맞은 시각 오래된 순, 없으면 [])
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
            long missed,                     // 빈칸 (알림이 갔는데 안 잰 측정)
            int enteredLate,                 // 나중 입력 (잰 뒤 30분 넘게 지나서 적음)
            int below70,                     // 70 미만
            int outOfTarget,                 // 목표 밖 (기록마다 그때 기준으로)
            Double inTargetRate,             // 목표 안 비율 0~1 (목표와 비교한 기록이 없으면 null)
            boolean compared) {              // 목표와 비교한 기록이 하나라도 있는지
    }

    // slot이 null이면 잴 때를 고르지 않은 기록 묶음
    public record SlotCount(MeasureSlot slot, int measured, int outOfTarget) {
    }

    public record Row(
            Long readingId,
            Instant measuredAt,
            ReadingType readingType,
            MeasureSlot slot,
            int value,
            Boolean aboveTarget,             // 그때 목표가 없었으면 null
            boolean enteredLate) {

        public static Row of(Reading r, Boolean aboveTarget) {
            return new Row(r.getId(), r.getMeasuredAt(), r.getReadingType(), r.getSlot(), r.getValue(),
                    aboveTarget, r.isEnteredLate());
        }
    }

    // 인슐린은 목표 비교가 없어서 맞은 그대로만 (해석·합계 없음). 「인슐린 · 저녁 식사 때 6단위」
    public record InsulinRow(
            Long insulinLogId,
            Instant takenAt,
            InsulinType insulinType,
            MealSlot mealSlot,               // 식사 때면 아침·점심·저녁, 자기 전이면 null
            BigDecimal units,
            boolean edited) {                // 저장한 뒤 고친 적이 있는지 (「수정됨」)

        public static InsulinRow of(InsulinLog l) {
            return new InsulinRow(l.getId(), l.getTakenAt(), l.getInsulinType(), l.getMealSlot(), l.getUnits(),
                    l.isEdited());
        }
    }
}
