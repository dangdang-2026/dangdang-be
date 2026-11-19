package com.dangdang.domain.visit.repository;

import com.dangdang.domain.visit.entity.VisitQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// 진료 질문 DB 저장·조회
public interface VisitQuestionRepository extends JpaRepository<VisitQuestion, Long> {

    // 이 일정의 살아 있는 질문 전부: 정렬 순서 → 먼저 만든 순
    List<VisitQuestion> findAllByVisitIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(Long visitId);

    // 이 일정에 붙은 이 질문 (지운 것도 찾음 → 다시 지우기 요청에 처음 지운 시각으로 답하려고)
    // 다른 일정의 질문 id로는 못 찾음
    Optional<VisitQuestion> findByIdAndVisitId(Long id, Long visitId);

    // 이 일정의 살아 있는 질문 수 (정렬 순서를 안 보내면 맨 뒤로 넣으려고)
    long countByVisitIdAndDeletedAtIsNull(Long visitId);
}
