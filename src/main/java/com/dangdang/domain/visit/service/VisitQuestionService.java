package com.dangdang.domain.visit.service;

import com.dangdang.domain.card.entity.ContentCard;
import com.dangdang.domain.card.enums.CardStatus;
import com.dangdang.domain.card.repository.ContentCardRepository;
import com.dangdang.domain.chat.entity.ChatMessage;
import com.dangdang.domain.chat.enums.ChatKind;
import com.dangdang.domain.chat.repository.ChatMessageRepository;
import com.dangdang.domain.user.service.SensitiveConsentGuard;
import com.dangdang.domain.visit.dto.request.VisitQuestionCreateRequest;
import com.dangdang.domain.visit.dto.response.VisitQuestionDeleteResponse;
import com.dangdang.domain.visit.dto.response.VisitQuestionResponse;
import com.dangdang.domain.visit.entity.Visit;
import com.dangdang.domain.visit.entity.VisitQuestion;
import com.dangdang.domain.visit.exception.VisitErrorCode;
import com.dangdang.domain.visit.repository.VisitQuestionRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

// 진료 질문. 모든 요청에서 먼저 일정이 내 것(삭제 안 된 것)인지 확인함
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VisitQuestionService {

    private final VisitQuestionRepository visitQuestionRepository;
    private final VisitService visitService;
    private final ChatMessageRepository chatMessageRepository;
    private final Clock clock;
    private final SensitiveConsentGuard sensitiveConsentGuard;
    private final ContentCardRepository contentCardRepository;

    // 목록: 정렬 순서 → 먼저 만든 순. 없으면 []
    public List<VisitQuestionResponse> list(Long userId, Long visitId) {
        visitService.findMine(userId, visitId);
        return visitQuestionRepository.findAllByVisitIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(visitId).stream()
                .map(VisitQuestionResponse::from)
                .toList();
    }

    // 등록. source는 클라이언트가 글자로 정하지 않는다
    // chatMessageId가 오면 내 대화 중 「물어볼 말 정리」(QUESTION_DRAFT) 답인지 확인하고 CHATBOT, 아니면 USER
    // cardId가 오면 올라가 있는 카드의 질문 중 하나와 같은지 확인하고 CARD, 아니면 USER (U-28)
    // 정렬 순서를 안 보내면 맨 뒤 = 지금 가장 큰 정렬 순서 + 1 (개수로 정하면 앞 질문이 10처럼 큰 값일 때 그 앞에 끼어듦)
    @Transactional
    public VisitQuestionResponse create(Long userId, Long visitId, VisitQuestionCreateRequest req) {
        sensitiveConsentGuard.require(userId);   // 건강정보 동의가 없으면 403
        // 일정 줄을 FOR SHARE로 잡는다 → 일정·병원 지우기와 겹쳐도 지운 일정에 살아 있는 질문·메모가 남지 않는다
        Visit visit = visitService.findMineForShare(userId, visitId);
        int sortOrder = req.sortOrder() != null ? req.sortOrder() : nextSortOrder(visitId);
        String content = req.trimmedContent();
        VisitQuestion question;
        if (req.chatMessageId() != null) {
            ChatMessage draft = chatMessageRepository.findByIdAndUserId(req.chatMessageId(), userId)
                    .filter(m -> m.getKind() == ChatKind.QUESTION_DRAFT)
                    .orElseThrow(() -> new CustomException(VisitErrorCode.CHAT_DRAFT_NOT_FOUND));
            // 초안과 같은 글일 때만 CHATBOT — 사용자가 고쳐 적었으면 사용자 질문(USER) (PR #120 리뷰: 아무 글이나 CHATBOT으로 꾸미지 못하게)
            question = matchesDraft(draft, content)
                    ? VisitQuestion.ofChatbot(visit, content, sortOrder)
                    : VisitQuestion.ofUser(visit, content, sortOrder);
        } else if (req.cardId() != null) {
            // 올라가 있는 카드만 (내려간 카드·쓰는 중 카드는 사용자 화면에 「물어볼 것에 적기」가 없음)
            // 공유 잠금으로 읽어서, 운영자가 그 카드를 내리는 중이면 끝난 뒤의 상태를 보고 막는다 (소식 걸기와 같음)
            ContentCard card = contentCardRepository.findByIdForShare(req.cardId())
                    .filter(c -> CardStatus.LIVE.contains(c.getStatus()))
                    .orElseThrow(() -> new CustomException(VisitErrorCode.CARD_NOT_FOUND));
            // 카드 질문 그대로일 때만 CARD. 고쳐 적었으면 사용자 질문(USER) (챗봇 질문과 같은 규칙)
            question = matchesCard(card, content)
                    ? VisitQuestion.ofCard(visit, content, sortOrder, card)
                    : VisitQuestion.ofUser(visit, content, sortOrder);
        } else {
            question = VisitQuestion.ofUser(visit, content, sortOrder);
        }
        VisitQuestion saved = visitQuestionRepository.saveAndFlush(question);
        return VisitQuestionResponse.from(saved);
    }

    // 삭제 (soft delete). 이미 지운 질문을 또 지우면 처음 지운 시각으로 200 (명세: 여러 번 해도 같은 결과)
    // 다른 일정의 질문이거나 없는 질문이면 404
    @Transactional
    public VisitQuestionDeleteResponse delete(Long userId, Long visitId, Long questionId) {
        visitService.findMine(userId, visitId);
        VisitQuestion question = visitQuestionRepository.findByIdAndVisitIdForUpdate(questionId, visitId)
                .orElseThrow(() -> new CustomException(VisitErrorCode.QUESTION_NOT_FOUND));
        question.delete(clock.instant());   // 변경 감지로 트랜잭션 끝날 때 반영됨
        return VisitQuestionDeleteResponse.from(question);
    }

    // 초안 전체이거나 초안의 한 줄(앞의 「-」 「•」 「1.」 같은 머리표는 뺌)과 글자가 같으면 당당이 질문으로 본다 (띄어쓰기 여러 칸은 한 칸으로)
    static boolean matchesDraft(ChatMessage draft, String content) {
        String draftText = draft.getPayload() == null ? null : draft.getPayload().questionDraft();
        if (draftText == null || draftText.isBlank()) {
            return false;
        }
        String target = normalize(content);
        if (normalize(draftText).equals(target)) {
            return true;
        }
        for (String line : draftText.split("\\R")) {
            if (normalize(line.replaceFirst("^\\s*(?:[-•·*]|\\d+[.)])\\s*", "")).equals(target)) {
                return true;
            }
        }
        return false;
    }

    // 카드 질문 중 하나와 글자가 같으면 카드 질문으로 본다 (앞뒤 공백, 띄어쓰기 여러 칸 차이는 봐줌)
    static boolean matchesCard(ContentCard card, String content) {
        String target = normalize(content);
        return card.getQuestions().stream()
                .anyMatch(q -> q != null && normalize(q).equals(target));
    }

    private static String normalize(String text) {
        return text.strip().replaceAll("\\s+", " ");
    }

    // 맨 뒤 정렬 순서. 가장 큰 값이 int 끝이면 +1 하면 음수가 되니 그대로 씀 (같은 값이면 먼저 만든 순이라 그래도 맨 뒤)
    private int nextSortOrder(Long visitId) {
        int max = visitQuestionRepository.findMaxSortOrder(visitId);
        return max == Integer.MAX_VALUE ? max : max + 1;
    }
}
