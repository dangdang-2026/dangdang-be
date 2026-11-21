package com.dangdang.domain.visit.service;

import com.dangdang.domain.caresite.exception.CareSiteErrorCode;
import com.dangdang.domain.caresite.repository.CareSiteRepository;
import com.dangdang.domain.criteria.entity.CriteriaVersion;
import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.criteria.repository.CriteriaVersionRepository;
import com.dangdang.domain.pregnancy.entity.Pregnancy;
import com.dangdang.domain.pregnancy.enums.PregnancyState;
import com.dangdang.domain.pregnancy.repository.PregnancyRepository;
import com.dangdang.domain.reading.entity.Reading;
import com.dangdang.domain.reading.repository.ReadingRepository;
import com.dangdang.domain.reading.service.ReadingEvaluator;
import com.dangdang.domain.reading.service.ReadingStatsService;
import com.dangdang.domain.reminder.repository.ReminderRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.domain.visit.dto.request.VisitReportCreateRequest;
import com.dangdang.domain.visit.dto.response.VisitReportResponse;
import com.dangdang.domain.visit.dto.response.VisitReportSaveResponse;
import com.dangdang.domain.visit.entity.SavedReport;
import com.dangdang.domain.visit.entity.Visit;
import com.dangdang.domain.visit.enums.ReportKind;
import com.dangdang.domain.visit.exception.VisitErrorCode;
import com.dangdang.domain.visit.repository.SavedReportRepository;
import com.dangdang.domain.visit.repository.VisitQuestionRepository;
import com.dangdang.domain.visit.repository.VisitRepository;
import com.dangdang.global.common.KoreanTime;
import com.dangdang.global.common.QueryParams;
import com.dangdang.global.exception.CustomException;
import com.dangdang.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 진료 리포트 (C-06 리포트, C-07 저장·공유, C-24 지난 리포트, C-25 기간 골라 만들기)
// 리포트 숫자·표는 저장하지 않고 볼 때마다 다시 계산함. saved_reports엔 기간·병원만 남김
@Service
@RequiredArgsConstructor
public class VisitReportService {

    // 한 리포트 기간 최대 길이 (임신 기간 전체 280일 + 여유, 혈당 통계 기간 직접 고르기와 같음)
    static final int MAX_DAYS = 300;
    // 이 값 미만이면 「70 미만」으로 셈
    private static final int LOW_VALUE = 70;
    // 임신 기간 (예정일에서 거꾸로 세는 기준)
    private static final int PREGNANCY_DAYS = 280;

    private final SavedReportRepository savedReportRepository;
    private final VisitService visitService;
    private final VisitRepository visitRepository;
    private final VisitQuestionRepository visitQuestionRepository;
    private final UserRepository userRepository;
    private final CareSiteRepository careSiteRepository;
    private final ReadingRepository readingRepository;
    private final ReminderRepository reminderRepository;
    private final CriteriaVersionRepository criteriaVersionRepository;
    private final PregnancyRepository pregnancyRepository;
    private final Clock clock;

    // 저장. 같은 kind·기간·진료 일정·병원이 이미 있으면 새로 만들지 않고 그 id를 돌려줌 (created=false → 200)
    // 병원: 기간 직접은 사용자가 고른 병원 (같은 기간이라도 병원이 다르면 따로 저장 → C-24 병원 필터에 맞게 보임)
    //       진료용은 보낸 값을 쓰지 않고 그 진료 일정의 병원 (같은 진료 리포트가 병원만 달리 두 개 생기지 않게)
    // 메서드에 트랜잭션을 걸지 않음: 같은 리포트가 동시에 와서 유니크 인덱스에 걸리면
    // 실패한 트랜잭션 밖에서 먼저 저장된 줄을 다시 읽어 돌려줘야 해서 (저장소 호출마다 짧은 트랜잭션)
    public VisitReportSaveResponse save(Long userId, VisitReportCreateRequest req) {
        checkPeriod(req.from(), req.to());
        Long careSiteId;
        if (req.kind() == ReportKind.VISIT) {
            careSiteId = visitOf(userId, req).getCareSiteId();
        } else {
            checkNoVisit(req);
            checkMyCareSite(userId, req.careSiteId());
            careSiteId = req.careSiteId();
        }

        var same = savedReportRepository.findSame(userId, req.kind(), req.from(), req.to(), req.visitId(), careSiteId);
        if (same.isPresent()) {
            return new VisitReportSaveResponse(same.get().getId(), false);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        try {
            SavedReport saved = savedReportRepository.saveAndFlush(
                    SavedReport.of(user, req.kind(), req.from(), req.to(), req.visitId(), careSiteId));
            return new VisitReportSaveResponse(saved.getId(), true);
        } catch (DataIntegrityViolationException e) {
            // 그사이 같은 리포트가 먼저 저장됨 → 그걸 돌려줌. 그래도 없으면 다른 제약에 걸린 것이라 그대로 던짐
            return savedReportRepository.findSame(userId, req.kind(), req.from(), req.to(), req.visitId(), careSiteId)
                    .map(r -> new VisitReportSaveResponse(r.getId(), false))
                    .orElseThrow(() -> e);
        }
    }

    // 진료용 리포트 (C-06). 기간 = 바로 앞 진료일 ~ 이번 진료 전날
    // 앞 진료가 없으면 첫 혈당 기록일부터, 기록도 없으면 전날 하루. 너무 길면 끝에서 MAX_DAYS일만
    @Transactional(readOnly = true)
    public VisitReportResponse visitReport(Long userId, Long visitId) {
        Visit visit = visitService.findMine(userId, visitId);
        if (visit.getVisitAt() == null) {
            throw new CustomException(VisitErrorCode.VISIT_DATE_UNDECIDED);
        }
        LocalDate visitDate = KoreanTime.dateOf(visit.getVisitAt());
        LocalDate to = visitDate.minusDays(1);

        // 같은 날 진료가 둘이어도 앞 진료로 치지 않게, 진료 날 0시보다 앞선 진료만 봄
        Instant lastVisitAt = visitRepository.findLastVisitAtBefore(userId, KoreanTime.startOfDay(visitDate));
        Instant start = lastVisitAt != null ? lastVisitAt : readingRepository.findFirstMeasuredAt(userId);
        LocalDate from = start == null ? to : KoreanTime.dateOf(start);
        if (from.isAfter(to)) {
            from = to;   // 첫 기록이 진료 날이나 그 뒤
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            from = to.minusDays(MAX_DAYS - 1L);
        }
        return build(userId, ReportKind.VISIT, from, to, visit, visit.getCareSiteId());
    }

    // 저장한 리포트 다시 보기 (C-24에서 누름). 저장한 기간·병원 그대로, 숫자는 지금 기록으로 다시 계산
    // 남의 것·없는 것, 그리고 진료용인데 그 진료 일정을 지웠으면 404 (일정을 지우면 그 리포트도 안 보이게)
    @Transactional(readOnly = true)
    public VisitReportResponse savedReport(Long userId, Long reportId) {
        SavedReport saved = savedReportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new CustomException(VisitErrorCode.REPORT_NOT_FOUND));
        Visit visit = null;
        if (saved.getKind() == ReportKind.VISIT) {
            visit = visitRepository.findByIdAndUserIdAndDeletedAtIsNull(saved.getVisitId(), userId)
                    .orElseThrow(() -> new CustomException(VisitErrorCode.REPORT_NOT_FOUND));
        }
        return build(userId, saved.getKind(), saved.getFromDate(), saved.getToDate(), visit, saved.getCareSiteId());
    }

    // 리포트 한 장 계산 (진료용·저장한 리포트가 같이 씀). visit은 진료용일 때만
    private VisitReportResponse build(Long userId, ReportKind kind, LocalDate from, LocalDate to,
                                      Visit visit, Long careSiteId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        List<Reading> readings = readingRepository.findAllInRange(userId,
                KoreanTime.startOfDay(from), KoreanTime.endOfDay(to));

        List<Boolean> above = readings.stream().map(ReadingEvaluator::aboveTarget).toList();
        List<VisitReportResponse.Row> rows = new ArrayList<>();
        for (int i = 0; i < readings.size(); i++) {
            rows.add(VisitReportResponse.Row.of(readings.get(i), above.get(i)));
        }

        return new VisitReportResponse(
                kind, from, to,
                visit == null ? null : visit.getId(),
                visit == null || visit.getVisitAt() == null ? null : KoreanTime.dateOf(visit.getVisitAt()),   // 저장 뒤 날짜를 미정으로 바꿨을 수 있음
                careSiteOf(userId, careSiteId),
                user.getDisplayName(),
                pregnancyWeeks(userId, from, to),
                criteriaIn(userId, from, to),
                summary(userId, rows, from, to),
                slotCounts(rows),
                rows,
                visit == null ? null : visitQuestionRepository.countByVisitIdAndDeletedAtIsNull(visit.getId()));
    }

    private VisitReportResponse.Summary summary(Long userId, List<VisitReportResponse.Row> rows,
                                                LocalDate from, LocalDate to) {
        // 목표가 있었던 기록만 안/밖을 셈 (혈당 통계와 같은 방식)
        long judged = rows.stream().filter(r -> r.aboveTarget() != null).count();
        int outOfTarget = (int) rows.stream().filter(r -> Boolean.TRUE.equals(r.aboveTarget())).count();
        Double inTargetRate = judged == 0 ? null : Math.round((judged - outOfTarget) * 100.0 / judged) / 100.0;
        return new VisitReportResponse.Summary(
                rows.size(),
                missed(userId, from, to),
                (int) rows.stream().filter(VisitReportResponse.Row::enteredLate).count(),
                (int) rows.stream().filter(r -> r.value() < LOW_VALUE).count(),
                outOfTarget,
                inTargetRate,
                judged > 0);
    }

    // 빈칸 = 기간 안 놓친 측정 알림 수 (혈당 통계 「놓친 측정」과 같은 기준, 모든 잴 때)
    // 알림 시각에서 MISSED_GRACE가 안 지난 알림은 아직 잴 시간이 있어서 안 셈
    private long missed(Long userId, LocalDate from, LocalDate to) {
        Instant rangeEnd = KoreanTime.endOfDay(to);
        Instant graceEnd = clock.instant().minus(ReadingStatsService.MISSED_GRACE);
        Instant end = rangeEnd.isBefore(graceEnd) ? rangeEnd : graceEnd;
        return reminderRepository.countByUserIdAndSlotInAndStatusInAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
                userId, List.of(MeasureSlot.values()), ReadingStatsService.MISSED_STATUSES,
                KoreanTime.startOfDay(from), end);
    }

    // 잴 때별 목표 밖 / 측정. 하루 순서(공복 → 아침·점심·저녁 식후 → 자기 전), 잴 때를 안 고른 기록은 맨 뒤
    // 기록이 있는 잴 때만 나옴
    private List<VisitReportResponse.SlotCount> slotCounts(List<VisitReportResponse.Row> rows) {
        Map<MeasureSlot, List<VisitReportResponse.Row>> bySlot = new LinkedHashMap<>();
        for (MeasureSlot slot : MeasureSlot.values()) {
            bySlot.put(slot, new ArrayList<>());
        }
        bySlot.put(null, new ArrayList<>());
        rows.forEach(r -> bySlot.get(r.slot()).add(r));
        return bySlot.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(e -> new VisitReportResponse.SlotCount(e.getKey(), e.getValue().size(),
                        (int) e.getValue().stream().filter(r -> Boolean.TRUE.equals(r.aboveTarget())).count()))
                .toList();
    }

    // 기간 안 날마다 적용된 병원 기준 (오래된 순). 날마다 적용 기준 = 적용일 ≤ 그날인 것 중 적용일 가장 늦고 id 가장 큰 것
    // → 시작일에 적용 중이던 기준 + 기간 안에서 새로 적용되기 시작한 기준(같은 날 여러 번 고쳤으면 마지막 것)
    private List<VisitReportResponse.Criteria> criteriaIn(Long userId, LocalDate from, LocalDate to) {
        List<CriteriaVersion> newestFirst = criteriaVersionRepository.findAllByUserIdOrderByEffectiveFromDescIdDesc(userId);
        List<CriteriaVersion> picked = new ArrayList<>();
        LocalDate lastDate = null;
        for (CriteriaVersion c : newestFirst) {
            LocalDate date = c.getEffectiveFrom();
            if (date.isAfter(to) || date.equals(lastDate)) {
                continue;   // 기간 뒤에 적용됐거나, 같은 날 더 나중 것을 이미 고름
            }
            picked.add(c);
            lastDate = date;
            if (!date.isAfter(from)) {
                break;      // 시작일에 적용 중이던 기준까지 고름
            }
        }
        return picked.reversed().stream().map(VisitReportResponse.Criteria::from).toList();
    }

    // 임신 주수 (예정일 기준 280일에서 거꾸로 셈, 내 정보 API와 같은 방식). 진행 중인 임신·예정일이 없으면 null
    private VisitReportResponse.PregnancyWeeks pregnancyWeeks(Long userId, LocalDate from, LocalDate to) {
        LocalDate dueDate = pregnancyRepository.findByUserIdAndStateNot(userId, PregnancyState.ENDED)
                .map(Pregnancy::getDueDate)
                .orElse(null);
        if (dueDate == null) {
            return null;
        }
        return new VisitReportResponse.PregnancyWeeks(weekOn(dueDate, from), weekOn(dueDate, to));
    }

    private static Integer weekOn(LocalDate dueDate, LocalDate date) {
        long elapsed = PREGNANCY_DAYS - ChronoUnit.DAYS.between(date, dueDate);
        return elapsed < 0 ? null : (int) (elapsed / 7);   // 예정일이 280일보다 더 뒤면 잘못 적은 날짜라 안 보여 줌
    }

    // 내 병원(삭제 안 된 것)이면 이름까지. 지운 병원이면 null
    private VisitReportResponse.CareSite careSiteOf(Long userId, Long careSiteId) {
        if (careSiteId == null) {
            return null;
        }
        return careSiteRepository.findByIdAndUserIdAndDeletedAtIsNull(careSiteId, userId)
                .map(c -> new VisitReportResponse.CareSite(c.getId(), c.getName()))
                .orElse(null);
    }

    // 기간: 2000~2100년, 시작 ≤ 끝, 최대 MAX_DAYS일 (틀리면 400 GLOBAL_40001)
    private void checkPeriod(LocalDate from, LocalDate to) {
        if (from.getYear() < QueryParams.MIN_YEAR || to.getYear() > QueryParams.MAX_YEAR) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT,
                    "기간은 " + QueryParams.MIN_YEAR + "~" + QueryParams.MAX_YEAR + "년 사이로 보내 주세요.");
        }
        if (from.isAfter(to)) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, "from이 to보다 늦어요.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, "리포트 기간은 " + MAX_DAYS + "일까지예요.");
        }
    }

    // 진료용은 내 진료 일정(삭제 안 된 것)이 있어야 함. 없음·삭제됨·남의 일정이면 VISIT_40401
    private Visit visitOf(Long userId, VisitReportCreateRequest req) {
        if (req.visitId() == null) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, "진료용 리포트는 visitId를 같이 보내 주세요.");
        }
        return visitService.findMine(userId, req.visitId());
    }

    // 기간 직접은 진료 일정을 보내지 않음
    private void checkNoVisit(VisitReportCreateRequest req) {
        if (req.visitId() != null) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, "기간 직접 리포트는 visitId를 보내지 않아요.");
        }
    }

    // 병원을 골랐으면 내 병원(삭제 안 된 것)이어야 함. 없음·삭제됨·남의 병원은 병원 API와 같은 404
    private void checkMyCareSite(Long userId, Long careSiteId) {
        if (careSiteId != null && careSiteRepository.findByIdAndUserIdAndDeletedAtIsNull(careSiteId, userId).isEmpty()) {
            throw new CustomException(CareSiteErrorCode.CARE_SITE_NOT_FOUND);
        }
    }
}
