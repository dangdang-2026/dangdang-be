package com.dangdang.domain.visit.repository;

import com.dangdang.domain.visit.entity.VisitNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// 진료 메모 DB 저장·조회
public interface VisitNoteRepository extends JpaRepository<VisitNote, Long> {

    // 이 일정의 살아 있는 메모 전부: 먼저 쓴 순
    List<VisitNote> findAllByVisitIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(Long visitId);

    // 이 일정에 붙은 이 메모 + 삭제 안 된 것만 (다른 일정의 메모 id로는 못 찾음)
    Optional<VisitNote> findByIdAndVisitIdAndDeletedAtIsNull(Long id, Long visitId);
}
