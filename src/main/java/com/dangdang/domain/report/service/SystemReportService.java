package com.dangdang.domain.report.service;

import com.dangdang.domain.report.enums.ReportReason;
import com.dangdang.domain.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

// 서버가 직접 넣는 신고 (source=SYSTEM, 신고한 사람 없음)
// U-15b 「검토 중일 수 있어요」 · N-06: 올리기 전 안내를 보고도 올린 글을 운영자 처리함에 넣는다. 글은 막지 않는다
@Service
@RequiredArgsConstructor
public class SystemReportService {

    // 올리기 전 확인(precheck)이 내는 종류 → 신고 사유. 여기 없는 종류는 버린다
    private static final Map<String, ReportReason> REVIEW_REASONS = Map.of(
            "DOSE", ReportReason.MEDICAL_ADVICE,           // 약·용량
            "URGENT_MEDICAL", ReportReason.CRISIS,         // 위급·마음·저혈당은 처리함 맨 위 (자동 위급)
            "MIND", ReportReason.CRISIS,
            "LOW_GLUCOSE", ReportReason.CRISIS);
    private static final String DETAIL = "올리기 전 안내를 보고도 올림 (자동: %s)";

    private final ReportRepository reportRepository;
    private final Clock clock;

    // 같은 사유로 묶이는 종류는 한 줄로 (예: MIND·URGENT_MEDICAL → CRISIS 한 줄, detail에 두 종류). detail엔 아는 종류 이름만 들어간다
    @Transactional
    public void queuePostForReview(Long postId, Collection<String> kinds) {
        Map<ReportReason, Set<String>> kindsByReason = kinds.stream()
                .filter(REVIEW_REASONS::containsKey)
                .collect(Collectors.groupingBy(REVIEW_REASONS::get, () -> new EnumMap<>(ReportReason.class),
                        Collectors.toCollection(TreeSet::new)));
        Instant now = clock.instant();
        kindsByReason.forEach((reason, known) -> reportRepository.addSystemReport(
                postId, reason.name(), DETAIL.formatted(String.join(", ", known)), now));
    }
}
