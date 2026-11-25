package com.dangdang.domain.visit.service;

import com.dangdang.domain.caresite.entity.CareSite;
import com.dangdang.domain.caresite.exception.CareSiteErrorCode;
import com.dangdang.domain.caresite.repository.CareSiteRepository;
import com.dangdang.domain.criteria.entity.CriteriaVersion;
import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.criteria.repository.CriteriaVersionRepository;
import com.dangdang.domain.pregnancy.entity.Pregnancy;
import com.dangdang.domain.pregnancy.repository.PregnancyRepository;
import com.dangdang.domain.reading.entity.Reading;
import com.dangdang.domain.reading.enums.ReadingType;
import com.dangdang.domain.reading.repository.ReadingCounts;
import com.dangdang.domain.reading.repository.ReadingRepository;
import com.dangdang.domain.reading.service.ReadingEvaluator;
import com.dangdang.domain.reading.service.ReadingStatsService;
import com.dangdang.domain.reminder.repository.ReminderRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.domain.user.service.SensitiveConsentGuard;
import com.dangdang.domain.visit.dto.request.VisitReportCreateRequest;
import com.dangdang.domain.visit.dto.response.VisitReportPageResponse;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

// 진료 리포트 (C-06 리포트, C-07 저장·공유, C-24 지난 리포트, C-25 기간 골라 만들기)
// 리포트 숫자·표는 저장하지 않고 볼 때마다 다시 계산함
// 진료용은 기간(앞 진료일 ~ 진료 전날)·병원도 볼 때마다 진료 일정에서 계산 → 일정을 고쳐도 조회·저장본·목록이 항상 같음
@Service
@RequiredArgsConstructor
public class VisitReportService {

    // 한 리포트 기간 최대 길이 (임신 기간 전체 280일 + 여유, 혈당 통계 기간 직접 고르기와 같음)
    static final int MAX_DAYS = 300;
    // 이 값 미만이면 「70 미만」으로 셈
    private static final int LOW_VALUE = ReadingEvaluator.LOW_VALUE;
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
    private final SensitiveConsentGuard sensitiveConsentGuard;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    private record Period(LocalDate from, LocalDate to) {
    }

    // ---------- 저장 ----------

    // 저장. 같은 리포트가 이미 있으면 새로 만들지 않고 그 id를 돌려줌 (created=false → 200)
    // 진료용: 진료 일정 하나에 한 번 (기간·병원은 저장하지 않고 볼 때 계산하므로 보내도 안 씀). 날짜 미정 일정은 400
    // 기간 직접: 같은 기간·병원이면 한 번 (같은 기간이라도 병원이 다르면 따로 → C-24 병원 필터에 맞게 보임)
    // 메서드에 트랜잭션을 걸지 않음: 같은 리포트가 동시에 와서 유니크 인덱스에 걸리면
    // 실패한 트랜잭션 밖에서 먼저 저장된 줄을 다시 읽어 돌려줘야 해서 (저장소 호출마다 짧은 트랜잭션)
    public VisitReportSaveResponse save(Long userId, VisitReportCreateRequest req) {
        Supplier<Optional<SavedReport>> findSame;
        Function<User, SavedReport> newReport;
        if (req.kind() == ReportKind.VISIT) {
            Visit visit = visitOf(userId, req);
            if (visit.getVisitAt() == null) {
                throw new CustomException(VisitErrorCode.VISIT_DATE_UNDECIDED);   // 진료 리포트 조회와 같게
            }
            findSame = () -> savedReportRepository.findByUserIdAndKindAndVisitId(userId, ReportKind.VISIT, visit.getId());
            newReport = user -> SavedReport.ofVisit(user, visit.getId());
        } else {
            checkNoVisit(req);
            checkPeriod(req.from(), req.to());
            checkMyCareSite(userId, req.careSiteId());
            findSame = () -> savedReportRepository.findSameCustom(userId, req.from(), req.to(), req.careSiteId());
            newReport = user -> SavedReport.ofCustom(user, req.from(), req.to(), req.careSiteId());
        }

        Optional<SavedReport> same = findSame.get();
        if (same.isPresent()) {
            return new VisitReportSaveResponse(same.get().getId(), false);
        }
        try {
            // 저장만 짧은 트랜잭션으로 — 건강정보 동의 확인(잠금)과 저장이 한 트랜잭션이어야 철회와 겹치지 않음
            SavedReport saved = transactionTemplate.execute(status -> {
                sensitiveConsentGuard.require(userId);   // 건강정보 동의가 없으면 403
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
                return savedReportRepository.saveAndFlush(newReport.apply(user));
            });
            return new VisitReportSaveResponse(saved.getId(), true);
        } catch (DataIntegrityViolationException e) {
            // 그사이 같은 리포트가 먼저 저장됨 → 그걸 돌려줌. 그래도 없으면 다른 제약에 걸린 것이라 그대로 던짐
            return findSame.get()
                    .map(r -> new VisitReportSaveResponse(r.getId(), false))
                    .orElseThrow(() -> e);
        }
    }

    // ---------- 조회 ----------

    // 진료용 리포트 (C-06). 없음·삭제됨·남의 일정이면 VISIT_40401, 날짜 미정이면 VISIT_40003
    @Transactional(readOnly = true)
    public VisitReportResponse visitReport(Long userId, Long visitId) {
        return reportOfVisit(userId, visitService.findMine(userId, visitId));
    }

    // 저장한 리포트 다시 보기 (C-24에서 누름). 숫자는 지금 기록으로 다시 계산
    // 진료용은 진료 리포트 조회와 똑같이 지금 진료 일정으로 기간·병원을 계산, 기간 직접은 저장한 기간·병원 그대로
    // 남의 것·없는 것, 그리고 진료용인데 그 진료 일정을 지웠으면 404 (일정을 지우면 그 리포트도 안 보이게)
    @Transactional(readOnly = true)
    public VisitReportResponse savedReport(Long userId, Long reportId) {
        SavedReport saved = savedReportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new CustomException(VisitErrorCode.REPORT_NOT_FOUND));
        if (saved.getKind() == ReportKind.VISIT) {
            Visit visit = visitRepository.findByIdAndUserIdAndDeletedAtIsNull(saved.getVisitId(), userId)
                    .orElseThrow(() -> new CustomException(VisitErrorCode.REPORT_NOT_FOUND));
            return reportOfVisit(userId, visit);
        }
        return build(userId, ReportKind.CUSTOM, new Period(saved.getFromDate(), saved.getToDate()), null,
                saved.getCareSiteId());
    }

    // 진료 일정으로 리포트 한 장. 기간 = 바로 앞 진료일 ~ 이번 진료 전날
    private VisitReportResponse reportOfVisit(Long userId, Visit visit) {
        if (visit.getVisitAt() == null) {
            throw new CustomException(VisitErrorCode.VISIT_DATE_UNDECIDED);
        }
        LocalDate visitDate = KoreanTime.dateOf(visit.getVisitAt());
        // 같은 날 진료가 둘이어도 앞 진료로 치지 않게, 진료 날 0시보다 앞선 진료만 봄
        Instant lastVisitAt = visitRepository.findLastVisitAtBefore(userId, KoreanTime.startOfDay(visitDate));
        Instant firstMeasuredAt = lastVisitAt == null ? readingRepository.findFirstMeasuredAt(userId) : null;
        return build(userId, ReportKind.VISIT, visitPeriod(visitDate, lastVisitAt, firstMeasuredAt), visit,
                visit.getCareSiteId());
    }

    // 진료용 기간: 앞 진료가 있으면 그날부터, 없으면 첫 혈당 기록일부터, 기록도 없으면 진료 전날 하루
    // 끝은 진료 전날. 너무 길면 끝에서 MAX_DAYS일만
    private static Period visitPeriod(LocalDate visitDate, Instant lastVisitAt, Instant firstMeasuredAt) {
        LocalDate to = visitDate.minusDays(1);
        Instant start = lastVisitAt != null ? lastVisitAt : firstMeasuredAt;
        LocalDate from = start == null ? to : KoreanTime.dateOf(start);
        if (from.isAfter(to)) {
            from = to;   // 첫 기록이 진료 날이나 그 뒤
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            from = to.minusDays(MAX_DAYS - 1L);
        }
        return new Period(from, to);
    }

    // ---------- 목록 ----------

    // 지난 리포트 목록 (C-24). 최근 저장 순, careSiteId를 주면 그 병원 것만
    // 카드 숫자(측정·목표 안 비율)는 리포트마다 DB에서 개수만 셈 (기록을 메모리에 올리지 않음)
    // 진료용 기간은 내 진료 시각 전부를 한 번 읽어 리포트마다 계산 (진료 리포트 조회와 같은 규칙)
    @Transactional(readOnly = true)
    public VisitReportPageResponse list(Long userId, Long careSiteId, int page, int size) {
        Slice<SavedReport> slice = savedReportRepository.findPage(userId, careSiteId, PageRequest.of(page, size));
        List<SavedReport> reports = slice.getContent();
        if (reports.isEmpty()) {
            return new VisitReportPageResponse(List.of(), slice.getNumber(), slice.getSize(), slice.hasNext());
        }

        List<Long> visitIds = reports.stream().map(SavedReport::getVisitId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Visit> visitById = visitRepository.findAllById(visitIds).stream()
                .collect(Collectors.toMap(Visit::getId, Function.identity()));
        List<Instant> allVisitAts = visitIds.isEmpty() ? List.of() : visitRepository.findAllVisitAts(userId);
        Instant firstMeasuredAt = visitIds.isEmpty() ? null : readingRepository.findFirstMeasuredAt(userId);

        Map<Long, String> careSiteNameById = careSiteNames(reports.stream()
                .map(r -> careSiteIdOf(r, visitById))
                .filter(Objects::nonNull).distinct().toList());

        List<VisitReportPageResponse.Item> items = new ArrayList<>();
        for (SavedReport r : reports) {
            Visit visit = r.getVisitId() == null ? null : visitById.get(r.getVisitId());
            Period period = null;
            LocalDate visitDate = null;
            if (r.getKind() == ReportKind.CUSTOM) {
                period = new Period(r.getFromDate(), r.getToDate());
            } else if (visit != null && visit.getVisitAt() != null) {
                visitDate = KoreanTime.dateOf(visit.getVisitAt());
                Instant before = KoreanTime.startOfDay(visitDate);
                Instant lastVisitAt = allVisitAts.stream().filter(at -> at.isBefore(before))
                        .max(Comparator.naturalOrder()).orElse(null);
                period = visitPeriod(visitDate, lastVisitAt, firstMeasuredAt);
            }   // 진료 날짜를 미정으로 바꿨으면 기간·숫자 없이 (상세는 VISIT_40003)

            Long itemCareSiteId = careSiteIdOf(r, visitById);
            items.add(new VisitReportPageResponse.Item(
                    r.getId(), r.getKind(),
                    period == null ? null : period.from(),
                    period == null ? null : period.to(),
                    r.getVisitId(),
                    itemCareSiteId,
                    itemCareSiteId == null ? null : careSiteNameById.get(itemCareSiteId),
                    visitDate,
                    period == null ? null : listSummary(userId, period),
                    r.getCreatedAt()));
        }
        return new VisitReportPageResponse(items, slice.getNumber(), slice.getSize(), slice.hasNext());
    }

    // 리포트의 병원: 진료용은 지금 진료 일정의 병원, 기간 직접은 저장한 병원
    private static Long careSiteIdOf(SavedReport r, Map<Long, Visit> visitById) {
        if (r.getKind() == ReportKind.CUSTOM) {
            return r.getCareSiteId();
        }
        Visit visit = visitById.get(r.getVisitId());
        return visit == null ? null : visit.getCareSiteId();
    }

    // 병원 이름 (지운 병원은 빼서 이름을 안 보여 줌, 리포트 상세와 같게)
    private Map<Long, String> careSiteNames(List<Long> careSiteIds) {
        if (careSiteIds.isEmpty()) {
            return Map.of();
        }
        return careSiteRepository.findAllById(careSiteIds).stream()
                .filter(c -> c.getDeletedAt() == null)
                .collect(Collectors.toMap(CareSite::getId, CareSite::getName));
    }

    // 목록 카드 숫자: 측정 수·목표 밖·목표 안 비율 (리포트 상세 summary와 같은 규칙, DB에서 셈)
    private VisitReportPageResponse.Summary listSummary(Long userId, Period period) {
        ReadingCounts counts = readingRepository.countInRange(userId,
                KoreanTime.startOfDay(period.from()), KoreanTime.endOfDay(period.to()),
                ReadingType.FASTING, ReadingType.POST_MEAL);
        int outOfTarget = (int) counts.outOfTarget();
        return new VisitReportPageResponse.Summary((int) counts.measured(),
                inTargetRate(counts.judged(), outOfTarget), outOfTarget, counts.judged() > 0);
    }

    // 목표 안 비율 (소수 둘째 자리 반올림). 목표와 비교한 기록이 없으면 null
    private static Double inTargetRate(long judged, int outOfTarget) {
        return judged == 0 ? null : Math.round((judged - outOfTarget) * 100.0 / judged) / 100.0;
    }

    // ---------- 리포트 한 장 계산 ----------

    // 진료용·기간 직접이 같이 씀. visit은 진료용일 때만
    private VisitReportResponse build(Long userId, ReportKind kind, Period period, Visit visit, Long careSiteId) {
        LocalDate from = period.from();
        LocalDate to = period.to();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        List<Reading> readings = readingRepository.findAllInRange(userId,
                KoreanTime.startOfDay(from), KoreanTime.endOfDay(to));

        List<VisitReportResponse.Row> rows = readings.stream()
                .map(r -> VisitReportResponse.Row.of(r, ReadingEvaluator.aboveTarget(r)))
                .toList();

        return new VisitReportResponse(
                kind, from, to,
                visit == null ? null : visit.getId(),
                visit == null ? null : KoreanTime.dateOf(visit.getVisitAt()),
                careSiteOf(userId, careSiteId),
                user.getDisplayName(),
                pregnancyWeeks(userId, from, to),
                criteriaIn(userId, from, to, readings),
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
        return new VisitReportResponse.Summary(
                rows.size(),
                missed(userId, from, to),
                (int) rows.stream().filter(VisitReportResponse.Row::enteredLate).count(),
                (int) rows.stream().filter(r -> r.value() < LOW_VALUE).count(),
                outOfTarget,
                inTargetRate(judged, outOfTarget),
                judged > 0);
    }

    // 빈칸 = 기간 안 놓친 측정 알림 수 (혈당 통계 「놓친 측정」과 같은 기준, 모든 잴 때)
    // 알림 시각에서 MISSED_GRACE가 안 지난 알림은 아직 잴 시간이 있어서 안 셈
    private long missed(Long userId, LocalDate from, LocalDate to) {
        Instant rangeEnd = KoreanTime.endOfDay(to);
        Instant graceEnd = clock.instant().minus(ReadingStatsService.MISSED_GRACE);
        Instant end = rangeEnd.isBefore(graceEnd) ? rangeEnd : graceEnd;
        return reminderRepository.countMissed(
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

    // 기간 안 병원 기준 (적용일 오래된 순 → id 순)
    // 1) 날마다 적용된 기준: 시작일에 적용 중이던 것 + 기간 안에서 새로 적용되기 시작한 것 (같은 날 여러 번 고쳤으면 마지막 것)
    // 2) 기간 안 기록들이 실제로 가리키는 기준 (목표 밖은 기록마다 이 기준으로 판정하므로, 같은 날 고치기 전에 잰 기록의 기준도 보이게)
    private List<VisitReportResponse.Criteria> criteriaIn(Long userId, LocalDate from, LocalDate to,
                                                          List<Reading> readings) {
        Map<Long, CriteriaVersion> picked = new LinkedHashMap<>();
        LocalDate lastDate = null;
        for (CriteriaVersion c : criteriaVersionRepository.findAllByUserIdOrderByEffectiveFromDescIdDesc(userId)) {
            LocalDate date = c.getEffectiveFrom();
            if (date.isAfter(to) || date.equals(lastDate)) {
                continue;   // 기간 뒤에 적용됐거나, 같은 날 더 나중 것을 이미 고름
            }
            picked.put(c.getId(), c);
            lastDate = date;
            if (!date.isAfter(from)) {
                break;      // 시작일에 적용 중이던 기준까지 고름
            }
        }
        readings.forEach(r -> picked.putIfAbsent(r.getCriteriaVersion().getId(), r.getCriteriaVersion()));
        return picked.values().stream()
                .sorted(Comparator.comparing(CriteriaVersion::getEffectiveFrom).thenComparing(CriteriaVersion::getId))
                .map(VisitReportResponse.Criteria::from)
                .toList();
    }

    // 임신 주수 (예정일 기준 280일에서 거꾸로 셈, 내 정보 API와 같은 방식)
    // 지금 진행 중인 임신이 아니라 리포트 기간과 겹치는 임신으로 셈 (출산 뒤 다음 임신 중에 옛 리포트를 열어도 그때 주수)
    // 겹치는 임신이 여럿이면 최근 것. 예정일이 없거나 겹치는 임신이 없으면 null
    private VisitReportResponse.PregnancyWeeks pregnancyWeeks(Long userId, LocalDate from, LocalDate to) {
        return pregnancyRepository.findAllByUserIdOrderByIdDesc(userId).stream()
                .filter(p -> p.getDueDate() != null && overlaps(p, from, to))
                .findFirst()
                .map(p -> new VisitReportResponse.PregnancyWeeks(weekOn(p, from), weekOn(p, to)))
                .orElse(null);
    }

    // 임신 기간 = 예정일 280일 전 ~ 출산일 (아직 안 낳았으면 끝 없음)
    private static boolean overlaps(Pregnancy p, LocalDate from, LocalDate to) {
        LocalDate start = p.getDueDate().minusDays(PREGNANCY_DAYS);
        return !start.isAfter(to) && (p.getDeliveryDate() == null || !p.getDeliveryDate().isBefore(from));
    }

    // 그날 주수. 임신 시작 전이거나 출산 뒤면 null
    private static Integer weekOn(Pregnancy p, LocalDate date) {
        LocalDate start = p.getDueDate().minusDays(PREGNANCY_DAYS);
        if (date.isBefore(start) || (p.getDeliveryDate() != null && date.isAfter(p.getDeliveryDate()))) {
            return null;
        }
        return (int) (ChronoUnit.DAYS.between(start, date) / 7);
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

    // ---------- 입력 검사 ----------

    // 기간 직접 기간: 둘 다 필수, 2000~2100년, 시작 ≤ 끝, 최대 MAX_DAYS일 (틀리면 400 GLOBAL_40001)
    private void checkPeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, "기간 직접 리포트는 from과 to를 같이 보내 주세요.");
        }
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
