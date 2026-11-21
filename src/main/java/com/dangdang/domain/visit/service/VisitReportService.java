package com.dangdang.domain.visit.service;

import com.dangdang.domain.caresite.exception.CareSiteErrorCode;
import com.dangdang.domain.caresite.repository.CareSiteRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.domain.visit.dto.request.VisitReportCreateRequest;
import com.dangdang.domain.visit.dto.response.VisitReportSaveResponse;
import com.dangdang.domain.visit.entity.SavedReport;
import com.dangdang.domain.visit.enums.ReportKind;
import com.dangdang.domain.visit.repository.SavedReportRepository;
import com.dangdang.global.common.QueryParams;
import com.dangdang.global.exception.CustomException;
import com.dangdang.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// 진료 리포트 (C-06 리포트, C-07 저장·공유, C-24 지난 리포트, C-25 기간 골라 만들기)
// 리포트 숫자·표는 저장하지 않고 볼 때마다 다시 계산함. saved_reports엔 기간·병원만 남김
@Service
@RequiredArgsConstructor
public class VisitReportService {

    // 한 리포트 기간 최대 길이 (임신 기간 전체 280일 + 여유, 혈당 통계 기간 직접 고르기와 같음)
    static final int MAX_DAYS = 300;

    private final SavedReportRepository savedReportRepository;
    private final VisitService visitService;
    private final UserRepository userRepository;
    private final CareSiteRepository careSiteRepository;

    // 저장. 같은 kind·기간·진료 일정이 이미 있으면 새로 만들지 않고 그 id를 돌려줌 (created=false → 200)
    // 메서드에 트랜잭션을 걸지 않음: 같은 리포트가 동시에 와서 유니크 인덱스에 걸리면
    // 실패한 트랜잭션 밖에서 먼저 저장된 줄을 다시 읽어 돌려줘야 해서 (저장소 호출마다 짧은 트랜잭션)
    public VisitReportSaveResponse save(Long userId, VisitReportCreateRequest req) {
        checkPeriod(req.from(), req.to());
        checkKind(userId, req);
        checkMyCareSite(userId, req.careSiteId());

        var same = savedReportRepository.findSame(userId, req.kind(), req.from(), req.to(), req.visitId());
        if (same.isPresent()) {
            return new VisitReportSaveResponse(same.get().getId(), false);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        try {
            SavedReport saved = savedReportRepository.saveAndFlush(
                    SavedReport.of(user, req.kind(), req.from(), req.to(), req.visitId(), req.careSiteId()));
            return new VisitReportSaveResponse(saved.getId(), true);
        } catch (DataIntegrityViolationException e) {
            // 그사이 같은 리포트가 먼저 저장됨 → 그걸 돌려줌. 그래도 없으면 다른 제약에 걸린 것이라 그대로 던짐
            return savedReportRepository.findSame(userId, req.kind(), req.from(), req.to(), req.visitId())
                    .map(r -> new VisitReportSaveResponse(r.getId(), false))
                    .orElseThrow(() -> e);
        }
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

    // 진료용은 내 진료 일정(삭제 안 된 것)이 있어야 하고, 기간 직접은 진료 일정을 보내지 않음
    private void checkKind(Long userId, VisitReportCreateRequest req) {
        if (req.kind() == ReportKind.VISIT) {
            if (req.visitId() == null) {
                throw new CustomException(GlobalErrorCode.INVALID_INPUT, "진료용 리포트는 visitId를 같이 보내 주세요.");
            }
            visitService.findMine(userId, req.visitId());   // 없음·삭제됨·남의 일정이면 VISIT_40401
        } else if (req.visitId() != null) {
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
