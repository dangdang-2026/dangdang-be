package com.dangdang.domain.visit.service;

import com.dangdang.domain.user.enums.ConsentType;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.service.ConsentService;
import com.dangdang.domain.user.service.SensitiveConsentGuard;
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

    static final int MAX_MEMO_FOR_AI = 3000;      // 메모 원문 길이 상한 (화면 안내와 같음)
    static final int MAX_MASKED_FOR_AI = 4000;    // 가린 글 상한 = AI 서버가 받는 길이 (dangdang-ai visit_note OrganizeRequest)

    private final VisitService visitService;
    private final VisitNoteRepository visitNoteRepository;
    private final VisitNoteOrganizeClient visitNoteOrganizeClient;
    private final VisitMemoMasker visitMemoMasker;
    private final AiGuard aiGuard;
    private final TransactionTemplate transactionTemplate;
    private final SensitiveConsentGuard sensitiveConsentGuard;
    private final ConsentService consentService;
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
        // AI 서버(해외)로 보낼 글 — 내 이름·병원 이름·전화번호를 자리표시로 (C-11). DB의 메모는 그대로
        // 원문(memo)은 4)의 「그사이 메모가 바뀌었나」 비교에만 쓴다
        // 자리표시가 원래 말보다 길 수 있어서 AI 서버는 4000자까지 받는다. 그래도 넘는 드문 경우만 같은 400
        String forAi = visitMemoMasker.mask(userId, memo);
        if (forAi.length() > MAX_MASKED_FOR_AI) {
            throw new CustomException(VisitErrorCode.NOTE_TOO_LONG_FOR_AI);
        }
        // 진료일: "2주 뒤 금요일"을 날짜로 바꿀 때 기준. 날짜 미정 일정이면 오늘 (메모는 보통 진료 당일·다음 날 씀)
        // 오늘도 한국 날짜로 (시계 설정의 시간대와 상관없이, AiGuard와 같은 방식)
        LocalDate visitDate = KoreanTime.dateOf(visit.getVisitAt() != null ? visit.getVisitAt() : clock.instant());

        // 건강정보(SENSITIVE) 동의가 없으면 메모를 AI(국외)로 보내기 전에 403 USER_40302 (재동의 필요 상태 포함)
        // AI 횟수도 쓰지 않는다. 저장할 때 잠그고 한 번 더 본다 (AI를 기다리는 사이 철회했을 수 있어서)
        if (!consentService.isGranted(userId, ConsentType.SENSITIVE)) {
            throw new CustomException(UserErrorCode.SENSITIVE_CONSENT_REQUIRED);
        }

        // 2) 문지기 — 국외 이전 동의가 없으면 403 AI_40301, 오늘 10번을 넘으면 429 AI_42901
        AiGuard.AiCall call = aiGuard.begin(userId, AiFeature.VISIT_NOTE);

        // 3) AI 서버 + 응답 정리. 어디서 어떤 예외가 나도 오늘 횟수를 돌려주고 「정리하지 못했어요」
        // 응답 정리: 빈 칸은 null, 확신 정도가 없거나 범위 밖이거나 근거가 메모에 없으면 「확인 필요」
        VisitNoteExtract extract;
        try {
            VisitNoteExtract raw = visitNoteOrganizeClient.organize(call.requestId(), forAi, visitDate);
            // 근거 문장은 AI가 받은 글(가린 글)에서 옮긴 것 → 가린 글과 비교해야 「메모에 있음」으로 맞는다
            extract = raw == null ? null : raw.normalized(forAi, visitDate);
        } catch (RuntimeException e) {
            aiGuard.fail(call, e.getClass().getSimpleName());
            throw new CustomException(VisitErrorCode.ORGANIZE_FAILED);
        }
        if (extract == null) {
            aiGuard.fail(call, "EMPTY_REPLY");
            throw new CustomException(VisitErrorCode.ORGANIZE_FAILED);
        }

        // 4) 저장. 메모 줄을 잠그고 다시 읽어서, AI를 기다리는 동안 메모가 바뀌었으면 옛 메모 기준 결과라 저장하지 않음
        // 저장까지 끝나야 오늘 횟수를 쓴 것으로 봄. 저장 못 하면(메모 바뀜·삭제됨) 결과를 못 받았으니 횟수를 돌려줌
        try {
            transactionTemplate.executeWithoutResult(status -> {
                sensitiveConsentGuard.require(userId);   // 그사이 건강정보 동의를 철회했으면 403 (메모 줄보다 먼저 잠금)
                VisitNote current = visitNoteRepository.findMineForUpdate(noteId, visitId)
                        .orElseThrow(() -> new CustomException(VisitErrorCode.NOTE_NOT_FOUND));   // 그사이 일정·메모가 지워짐
                if (!current.hasSameContent(memo)) {
                    throw new CustomException(VisitErrorCode.NOTE_CHANGED);
                }
                // 정리 결과 칸만 바꿈. 엔티티를 고치면 updatedAt도 바뀌어서 메모가 「수정됨」으로 보이므로 쿼리로 직접 씀
                visitNoteRepository.saveExtract(noteId, extract);
            });
        } catch (RuntimeException e) {
            aiGuard.fail(call, e instanceof CustomException ce ? ce.getErrorCode().getCode() : e.getClass().getSimpleName());
            throw e;
        }
        aiGuard.finish(call, extract.promptVersion(), "OK");
        return new VisitNoteOrganizeResponse(noteId, extract);
    }
}
