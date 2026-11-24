package com.dangdang.domain.visit.service;

import com.dangdang.domain.user.service.SensitiveConsentGuard;
import com.dangdang.domain.visit.dto.request.VisitNoteRequest;
import com.dangdang.domain.visit.dto.response.VisitNoteResponse;
import com.dangdang.domain.visit.entity.Visit;
import com.dangdang.domain.visit.entity.VisitNote;
import com.dangdang.domain.visit.exception.VisitErrorCode;
import com.dangdang.domain.visit.repository.VisitNoteRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 진료 메모. 모든 요청에서 먼저 일정이 내 것(삭제 안 된 것)인지 확인함
// 메모 저장·수정만으로는 AI 정리를 돌리지 않음 (AI 정리는 따로 동의·요청)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VisitNoteService {

    private final VisitNoteRepository visitNoteRepository;
    private final VisitService visitService;
    private final SensitiveConsentGuard sensitiveConsentGuard;

    // 조회: 먼저 쓴 순. 없으면 []
    public List<VisitNoteResponse> list(Long userId, Long visitId) {
        visitService.findMine(userId, visitId);
        return visitNoteRepository.findAllByVisitIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(visitId).stream()
                .map(VisitNoteResponse::from)
                .toList();
    }

    // 등록. 내용은 적은 그대로 저장
    @Transactional
    public VisitNoteResponse create(Long userId, Long visitId, VisitNoteRequest req) {
        sensitiveConsentGuard.require(userId);   // 건강정보 동의가 없으면 403
        Visit visit = visitService.findMine(userId, visitId);
        VisitNote saved = visitNoteRepository.saveAndFlush(VisitNote.of(visit, req.content()));
        return VisitNoteResponse.from(saved);
    }

    // 수정 (내용 통째로 바꿈). 다른 일정의 메모이거나 없는 메모면 404
    @Transactional
    public VisitNoteResponse update(Long userId, Long visitId, Long noteId, VisitNoteRequest req) {
        sensitiveConsentGuard.require(userId);   // 건강정보 동의가 없으면 403
        visitService.findMine(userId, visitId);
        // 줄을 잠그고 읽음: AI 정리 저장과 겹치면 하나가 끝난 뒤에 읽어서, 정리 결과를 옛 값으로 덮어쓰지 않게
        VisitNote note = visitNoteRepository.findMineForUpdate(noteId, visitId)
                .orElseThrow(() -> new CustomException(VisitErrorCode.NOTE_NOT_FOUND));
        note.changeContent(req.content());
        // 변경 감지로 트랜잭션 끝날 때 반영되지만, 응답에 새 updatedAt을 담으려고 먼저 DB에 씀
        visitNoteRepository.flush();
        return VisitNoteResponse.from(note);
    }
}
