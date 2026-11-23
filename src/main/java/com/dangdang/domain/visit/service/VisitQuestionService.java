package com.dangdang.domain.visit.service;

import com.dangdang.domain.chat.enums.ChatKind;
import com.dangdang.domain.chat.repository.ChatMessageRepository;
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

    // 목록: 정렬 순서 → 먼저 만든 순. 없으면 []
    public List<VisitQuestionResponse> list(Long userId, Long visitId) {
        visitService.findMine(userId, visitId);
        return visitQuestionRepository.findAllByVisitIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(visitId).stream()
                .map(VisitQuestionResponse::from)
                .toList();
    }

    // 등록. source는 클라이언트가 글자로 정하지 않는다
    // chatMessageId가 오면 내 대화 중 「물어볼 말 정리」(QUESTION_DRAFT) 답인지 확인하고 CHATBOT, 아니면 USER
    // 정렬 순서를 안 보내면 맨 뒤 = 지금 가장 큰 정렬 순서 + 1 (개수로 정하면 앞 질문이 10처럼 큰 값일 때 그 앞에 끼어듦)
    @Transactional
    public VisitQuestionResponse create(Long userId, Long visitId, VisitQuestionCreateRequest req) {
        Visit visit = visitService.findMine(userId, visitId);
        int sortOrder = req.sortOrder() != null ? req.sortOrder() : nextSortOrder(visitId);
        if (req.chatMessageId() != null) {
            chatMessageRepository.findByIdAndUserId(req.chatMessageId(), userId)
                    .filter(m -> m.getKind() == ChatKind.QUESTION_DRAFT)
                    .orElseThrow(() -> new CustomException(VisitErrorCode.CHAT_DRAFT_NOT_FOUND));
        }
        VisitQuestion question = req.chatMessageId() == null
                ? VisitQuestion.ofUser(visit, req.trimmedContent(), sortOrder)
                : VisitQuestion.ofChatbot(visit, req.trimmedContent(), sortOrder);
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

    // 맨 뒤 정렬 순서. 가장 큰 값이 int 끝이면 +1 하면 음수가 되니 그대로 씀 (같은 값이면 먼저 만든 순이라 그래도 맨 뒤)
    private int nextSortOrder(Long visitId) {
        int max = visitQuestionRepository.findMaxSortOrder(visitId);
        return max == Integer.MAX_VALUE ? max : max + 1;
    }
}
