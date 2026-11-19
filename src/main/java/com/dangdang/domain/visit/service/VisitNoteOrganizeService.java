package com.dangdang.domain.visit.service;

import com.dangdang.domain.visit.dto.response.VisitNoteOrganizeResponse;
import com.dangdang.domain.visit.entity.Visit;
import com.dangdang.domain.visit.entity.VisitNote;
import com.dangdang.domain.visit.entity.VisitNoteExtract;
import com.dangdang.domain.visit.exception.VisitErrorCode;
import com.dangdang.domain.visit.infra.VisitNoteOrganizeClient;
import com.dangdang.domain.visit.repository.VisitNoteRepository;
import com.dangdang.global.ai.AiFeature;
import com.dangdang.global.ai.AiGuard;
import com.dangdang.global.common.KoreanTime;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;

// 진료 메모 AI 정리 (C-11 「AI로 정리」 → C-12 결과)
// AI 서버를 기다리는 동안(최대 20초) DB 트랜잭션을 잡고 있지 않으려고 메서드 전체에 @Transactional을 걸지 않음
// 읽기 → AI 호출 → 저장(짧은 트랜잭션) 순서. 메모 내용·AI 결과는 로그에 남기지 않음 (AiGuard가 requestId·promptVersion만 남김)
@Service
@RequiredArgsConstructor
public class VisitNoteOrganizeService {

    static final int MAX_MEMO_FOR_AI = 3000;   // AI 서버가 받는 메모 길이

    private final VisitService visitService;
    private final VisitNoteRepository visitNoteRepository;
    private final VisitNoteOrganizeClient visitNoteOrganizeClient;
    private final AiGuard aiGuard;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public VisitNoteOrganizeResponse organize(Long userId, Long visitId, Long noteId) {
        // 1) 내 일정·그 일정의 메모인지 확인. 길이는 AI 횟수를 쓰기 전에 봄
        Visit visit = visitService.findMine(userId, visitId);
        VisitNote note = visitNoteRepository.findByIdAndVisitIdAndDeletedAtIsNull(noteId, visitId)
                .orElseThrow(() -> new CustomException(VisitErrorCode.NOTE_NOT_FOUND));
        String memo = note.getContent().strip();
        if (memo.length() > MAX_MEMO_FOR_AI) {
            throw new CustomException(VisitErrorCode.NOTE_TOO_LONG_FOR_AI);
        }
        // 진료일: "2주 뒤 금요일"을 날짜로 바꿀 때 기준. 날짜 미정 일정이면 오늘 (메모는 보통 진료 당일·다음 날 씀)
        LocalDate visitDate = visit.getVisitAt() != null
                ? KoreanTime.dateOf(visit.getVisitAt())
                : LocalDate.now(clock);

        // 2) 문지기 — 국외 이전 동의가 없으면 403 AI_40301, 오늘 10번을 넘으면 429 AI_42901
        AiGuard.AiCall call = aiGuard.begin(userId, AiFeature.VISIT_NOTE);

        // 3) AI 서버. 어떤 예외가 나도 오늘 횟수를 돌려주고 「정리하지 못했어요」
        VisitNoteExtract extract;
        try {
            extract = visitNoteOrganizeClient.organize(call.requestId(), memo, visitDate);
        } catch (RuntimeException e) {
            aiGuard.fail(call, e.getClass().getSimpleName());
            throw new CustomException(VisitErrorCode.ORGANIZE_FAILED);
        }
        if (extract == null) {
            aiGuard.fail(call, "EMPTY_REPLY");
            throw new CustomException(VisitErrorCode.ORGANIZE_FAILED);
        }
        aiGuard.finish(call, extract.promptVersion(), "OK");

        // 4) 저장. 메모 줄을 잠그고 다시 읽어서, AI를 기다리는 동안 메모가 바뀌었으면 옛 메모 기준 결과라 저장하지 않음
        transactionTemplate.executeWithoutResult(status -> {
            VisitNote current = visitNoteRepository.findMineForUpdate(noteId, visitId)
                    .orElseThrow(() -> new CustomException(VisitErrorCode.NOTE_NOT_FOUND));   // 그사이 일정·메모가 지워짐
            if (!current.getContent().strip().equals(memo)) {
                throw new CustomException(VisitErrorCode.NOTE_CHANGED);
            }
            current.saveExtract(extract);   // 변경 감지로 트랜잭션 끝날 때 반영됨
        });
        return new VisitNoteOrganizeResponse(noteId, extract);
    }
}
