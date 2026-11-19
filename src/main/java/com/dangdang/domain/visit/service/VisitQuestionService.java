package com.dangdang.domain.visit.service;

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
    private final Clock clock;

    // 목록: 정렬 순서 → 먼저 만든 순. 없으면 []
    public List<VisitQuestionResponse> list(Long userId, Long visitId) {
        visitService.findMine(userId, visitId);
        return visitQuestionRepository.findAllByVisitIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(visitId).stream()
                .map(VisitQuestionResponse::from)
                .toList();
    }

    // 등록. source는 항상 USER (클라이언트가 정하지 않음). 정렬 순서를 안 보내면 맨 뒤
    @Transactional
    public VisitQuestionResponse create(Long userId, Long visitId, VisitQuestionCreateRequest req) {
        Visit visit = visitService.findMine(userId, visitId);
        int sortOrder = req.sortOrder() != null
                ? req.sortOrder()
                : (int) visitQuestionRepository.countByVisitIdAndDeletedAtIsNull(visitId);
        VisitQuestion saved = visitQuestionRepository.saveAndFlush(
                VisitQuestion.ofUser(visit, req.trimmedContent(), sortOrder));
        return VisitQuestionResponse.from(saved);
    }

    // 삭제 (soft delete). 이미 지운 질문을 또 지우면 처음 지운 시각으로 200 (명세: 여러 번 해도 같은 결과)
    // 다른 일정의 질문이거나 없는 질문이면 404
    @Transactional
    public VisitQuestionDeleteResponse delete(Long userId, Long visitId, Long questionId) {
        visitService.findMine(userId, visitId);
        VisitQuestion question = visitQuestionRepository.findByIdAndVisitId(questionId, visitId)
                .orElseThrow(() -> new CustomException(VisitErrorCode.QUESTION_NOT_FOUND));
        question.delete(clock.instant());   // 변경 감지로 트랜잭션 끝날 때 반영됨
        return VisitQuestionDeleteResponse.from(question);
    }
}
