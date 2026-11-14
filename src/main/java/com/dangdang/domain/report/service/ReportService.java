package com.dangdang.domain.report.service;

import com.dangdang.domain.comment.service.CommentService;
import com.dangdang.domain.community.entity.CommunityProfile;
import com.dangdang.domain.community.service.CommunityProfileService;
import com.dangdang.domain.post.service.PostService;
import com.dangdang.domain.report.dto.request.ReportCreateRequest;
import com.dangdang.domain.report.dto.response.ReportResponse;
import com.dangdang.domain.report.enums.ReportReason;
import com.dangdang.domain.report.enums.ReportTargetType;
import com.dangdang.domain.report.exception.ReportErrorCode;
import com.dangdang.domain.report.repository.ReportRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final PostService postService;
    private final CommentService commentService;
    private final CommunityProfileService profileService;
    private final Clock clock;

    @Transactional
    public ReportResponse create(Long userId, ReportCreateRequest request) {
        CommunityProfile me = profileService.requireProfile(userId);
        if (isMine(request, me.getId())) {
            throw new CustomException(ReportErrorCode.CANNOT_REPORT_OWN);
        }
        String detail = request.reason() == ReportReason.ETC ? request.detail() : null;
        reportRepository.addIfAbsent(me.getId(), request.targetType().name(), request.targetId(),
                request.reason().name(), detail, clock.instant());
        return new ReportResponse(true);
    }

    // 대상이 안 보이면(없음·삭제·가림·차단) 여기서 404가 난다
    private boolean isMine(ReportCreateRequest request, Long myProfileId) {
        if (request.targetType() == ReportTargetType.POST) {
            return postService.findVisiblePost(request.targetId(), myProfileId).isOwnedBy(myProfileId);
        }
        return commentService.findVisibleComment(request.targetId(), myProfileId).isOwnedBy(myProfileId);
    }
}
