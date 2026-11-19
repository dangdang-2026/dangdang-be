package com.dangdang.domain.report.service;

import com.dangdang.domain.comment.entity.Comment;
import com.dangdang.domain.comment.repository.CommentRepository;
import com.dangdang.domain.community.entity.CommunityProfile;
import com.dangdang.domain.post.entity.Post;
import com.dangdang.domain.post.repository.PostRepository;
import com.dangdang.domain.report.dto.request.AdminHideRequest;
import com.dangdang.domain.report.dto.response.AdminReportActionResponse;
import com.dangdang.domain.report.dto.response.AdminReportDetailResponse;
import com.dangdang.domain.report.dto.response.AdminReportDetailResponse.ActionLine;
import com.dangdang.domain.report.dto.response.AdminReportDetailResponse.ReportLine;
import com.dangdang.domain.report.dto.response.AdminReportItemResponse;
import com.dangdang.domain.report.dto.response.AdminReportListResponse;
import com.dangdang.domain.report.entity.AdminAction;
import com.dangdang.domain.report.enums.AdminActionType;
import com.dangdang.domain.report.enums.ReportReason;
import com.dangdang.domain.report.enums.ReportStatus;
import com.dangdang.domain.report.enums.ReportTargetType;
import com.dangdang.domain.report.exception.ReportErrorCode;
import com.dangdang.domain.report.repository.AdminActionRepository;
import com.dangdang.domain.report.repository.ReportQueueRow;
import com.dangdang.domain.report.repository.ReportRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 운영자 신고 처리함 (COM-09 · 운영자 화면 구상 §2). 운영자는 글을 고치지 않고 가리기만 한다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportService {

    private static final int PREVIEW_LENGTH = 40;

    private final ReportRepository reportRepository;
    private final AdminActionRepository actionRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final Clock clock;

    public AdminReportListResponse list(ReportStatus status) {
        List<ReportQueueRow> rows = reportRepository.findQueue(status.name());
        Map<Long, Post> posts = postRepository.findAllById(idsOf(rows, ReportTargetType.POST)).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        Map<Long, Comment> comments = commentRepository.findAllById(idsOf(rows, ReportTargetType.COMMENT)).stream()
                .collect(Collectors.toMap(Comment::getId, Function.identity()));

        List<AdminReportItemResponse> items = rows.stream()
                .map(row -> toItem(row, posts, comments))
                .toList();
        return new AdminReportListResponse(items, reportRepository.countQueue(status.name()));
    }

    public AdminReportDetailResponse detail(ReportTargetType type, Long targetId) {
        Target target = findTarget(type, targetId);
        List<ReportLine> reports = reportRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(type, targetId)
                .stream()
                .map(r -> new ReportLine(r.getReason().name(), r.getDetail(), r.getStatus(), r.getCreatedAt()))
                .toList();
        List<ActionLine> actions = actionRepository.findByTargetTypeAndTargetIdOrderByOccurredAtDescIdDesc(type, targetId)
                .stream()
                .map(a -> new ActionLine(a.getAction().name(), a.getReason() == null ? null : a.getReason().name(),
                        a.getMemo(), a.getAdminId(), a.getOccurredAt()))
                .toList();
        CommunityProfile author = target.author();
        return new AdminReportDetailResponse(type.name(), String.valueOf(targetId), String.valueOf(target.postId()),
                target.title(), target.content(),
                author == null ? null : author.getUser().getId(),
                author == null ? null : author.getNickname(),
                target.anonymous(), target.createdAt(), target.state(), target.hiddenReason(), reports, actions);
    }

    // 가리기: 아직 안 가렸을 때만 사유를 적고(조건부 UPDATE), 접수된 신고를 HIDDEN으로
    @Transactional
    public AdminReportActionResponse hide(Long adminId, ReportTargetType type, Long targetId, AdminHideRequest request) {
        String reason = request.reason().name();
        int changed = type == ReportTargetType.POST
                ? postRepository.hide(targetId, reason)
                : commentRepository.hide(targetId, reason);
        if (changed == 0) {
            findTarget(type, targetId);                                  // 대상이 없으면 여기서 404
            throw new CustomException(ReportErrorCode.ALREADY_HIDDEN);  // 있으면 이미 가려진 것 (다른 운영자가 먼저)
        }
        Instant now = clock.instant();
        int handled = reportRepository.markHandled(type.name(), targetId, ReportStatus.HIDDEN.name(), now);
        return record(adminId, AdminActionType.HIDE, type, targetId, request.reason(), request.memo(), handled, now);
    }

    // 문제없음: 대상은 그대로, 접수된 신고만 DISMISSED로. 바꿀 신고가 없으면(이미 처리됨) 409
    @Transactional
    public AdminReportActionResponse dismiss(Long adminId, ReportTargetType type, Long targetId, String memo) {
        findTarget(type, targetId);                                      // 대상이 없으면 404
        Instant now = clock.instant();
        int handled = reportRepository.markHandled(type.name(), targetId, ReportStatus.DISMISSED.name(), now);
        if (handled == 0) {
            throw new CustomException(ReportErrorCode.NOTHING_TO_DISMISS);
        }
        return record(adminId, AdminActionType.DISMISS, type, targetId, null, memo, handled, now);
    }

    // 되돌리기 (이의 신청을 받아들임): 가림만 푼다. 지난 신고 기록은 그대로 둔다
    @Transactional
    public AdminReportActionResponse unhide(Long adminId, ReportTargetType type, Long targetId, String memo) {
        int changed = type == ReportTargetType.POST
                ? postRepository.unhide(targetId)
                : commentRepository.unhide(targetId);
        if (changed == 0) {
            findTarget(type, targetId);                                  // 대상이 없으면 404
            throw new CustomException(ReportErrorCode.NOT_HIDDEN);
        }
        return record(adminId, AdminActionType.UNHIDE, type, targetId, null, memo, 0, clock.instant());
    }

    // 처리 기록을 남기고, 바뀐 뒤 상태를 DB에서 다시 읽어 돌려준다
    private AdminReportActionResponse record(Long adminId, AdminActionType action, ReportTargetType type, Long targetId,
                                             ReportReason reason, String memo, int handled, Instant now) {
        actionRepository.save(AdminAction.of(adminId, action, type, targetId, reason, memo, now));
        String state = findTarget(type, targetId).state();
        return new AdminReportActionResponse(type.name(), String.valueOf(targetId), state, handled);
    }

    private List<Long> idsOf(List<ReportQueueRow> rows, ReportTargetType type) {
        return rows.stream().filter(r -> r.getTargetType().equals(type.name())).map(ReportQueueRow::getTargetId).toList();
    }

    private AdminReportItemResponse toItem(ReportQueueRow row, Map<Long, Post> posts, Map<Long, Comment> comments) {
        Target target = row.getTargetType().equals(ReportTargetType.POST.name())
                ? Target.of(posts.get(row.getTargetId()))
                : Target.of(comments.get(row.getTargetId()));
        List<String> reasons = Arrays.stream(row.getReasons().split(",")).sorted().toList();
        return new AdminReportItemResponse(row.getTargetType(), String.valueOf(row.getTargetId()),
                target == null ? null : String.valueOf(target.postId()),
                target == null ? null : target.preview(),
                target == null ? "DELETED" : target.state(),     // 글이 통째로 지워져 행이 없으면
                row.getReportCount(), reasons, row.getLastReportedAt());
    }

    // 운영자는 지운 글도 본다 (deleted_at이 있어도 찾음)
    private Target findTarget(ReportTargetType type, Long targetId) {
        Target target = type == ReportTargetType.POST
                ? postRepository.findById(targetId).map(Target::of).orElse(null)
                : commentRepository.findById(targetId).map(Target::of).orElse(null);
        if (target == null) {
            throw new CustomException(ReportErrorCode.TARGET_NOT_FOUND);
        }
        return target;
    }

    // 글과 댓글을 같은 모양으로 다루는 작은 그릇
    private record Target(Post post, Comment comment) {

        static Target of(Post post) {
            return post == null ? null : new Target(post, null);
        }

        static Target of(Comment comment) {
            return comment == null ? null : new Target(null, comment);
        }

        Long postId() {
            return post != null ? post.getId() : comment.getPost().getId();
        }

        String title() {
            return post != null ? post.getTitle() : null;
        }

        String content() {
            return post != null ? post.getContent() : comment.getContent();
        }

        String preview() {
            String text = post != null ? post.getTitle() : comment.getContent();
            return text.length() <= PREVIEW_LENGTH ? text : text.substring(0, PREVIEW_LENGTH) + "…";
        }

        CommunityProfile author() {
            return post != null ? post.getProfile() : comment.getProfile();
        }

        boolean anonymous() {
            return post != null ? post.isAnonymous() : comment.isAnonymous();
        }

        Instant createdAt() {
            return post != null ? post.getCreatedAt() : comment.getCreatedAt();
        }

        String hiddenReason() {
            return post != null ? post.getHiddenReason() : comment.getHiddenReason();
        }

        String state() {
            Instant deletedAt = post != null ? post.getDeletedAt() : comment.getDeletedAt();
            if (deletedAt != null) {
                return "DELETED";
            }
            return hiddenReason() != null ? "HIDDEN" : "VISIBLE";
        }
    }
}
