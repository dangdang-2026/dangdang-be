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
import java.util.Optional;
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
        long totalCount = rows.isEmpty() ? 0 : rows.get(0).getTotalCount();
        return new AdminReportListResponse(items, totalCount);
    }

    public AdminReportDetailResponse detail(ReportTargetType type, Long targetId) {
        Target target = findTarget(type, targetId);
        List<ReportLine> reports = reportRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(type, targetId)
                .stream()
                .map(r -> new ReportLine(r.getReason().name(), r.getDetail(), r.getStatus().name(),
                        r.getSource().name(), r.getCreatedAt()))
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
                target.anonymous(), target.createdAt(), target.updatedAt(), target.state(), target.hiddenReason(),
                reports, actions);
    }

    // 가리기: 대상 줄을 잠그고, 지운 것·이미 가린 것은 409. 접수·문제없음 신고를 모두 HIDDEN으로 (가림 상태와 신고 상태를 맞춘다)
    @Transactional
    public AdminReportActionResponse hide(Long adminId, ReportTargetType type, Long targetId, AdminHideRequest request) {
        Target target = lockAndFind(type, targetId)
                .orElseThrow(() -> new CustomException(ReportErrorCode.TARGET_NOT_FOUND));
        if (target.deleted()) {
            throw new CustomException(ReportErrorCode.DELETED_TARGET);
        }
        if (target.hiddenReason() != null) {
            throw new CustomException(ReportErrorCode.ALREADY_HIDDEN);       // 다른 운영자가 먼저 가림
        }
        String reason = request.reason().name();
        if (type == ReportTargetType.POST) {
            postRepository.hide(targetId, reason);
        } else {
            commentRepository.hide(targetId, reason);
        }
        Instant now = clock.instant();
        int handled = reportRepository.moveStatus(type.name(), targetId,
                List.of(ReportStatus.RECEIVED.name(), ReportStatus.DISMISSED.name()), ReportStatus.HIDDEN.name(), now);
        return record(adminId, AdminActionType.HIDE, type, targetId, request.reason(), request.memo(), handled, now);
    }

    // 문제없음: 대상은 그대로, 접수 신고만 DISMISSED로. 가린 대상은 409 (되돌리기를 먼저)
    // 탈퇴로 글이 통째로 지워져 대상이 없어도 남은 신고는 정리할 수 있다
    @Transactional
    public AdminReportActionResponse dismiss(Long adminId, ReportTargetType type, Long targetId, String memo) {
        Optional<Target> target = lockAndFind(type, targetId);
        if (target.isPresent() && target.get().hiddenReason() != null) {
            throw new CustomException(ReportErrorCode.ALREADY_HIDDEN);
        }
        Instant now = clock.instant();
        int handled = reportRepository.moveStatus(type.name(), targetId,
                List.of(ReportStatus.RECEIVED.name()), ReportStatus.DISMISSED.name(), now);
        if (handled == 0) {
            throw new CustomException(target.isPresent() ? ReportErrorCode.NOTHING_TO_DISMISS : ReportErrorCode.TARGET_NOT_FOUND);
        }
        return record(adminId, AdminActionType.DISMISS, type, targetId, null, memo, handled, now);
    }

    // 되돌리기 (이의 신청을 받아들임): 가림을 풀고, 가림으로 처리했던 신고는 DISMISSED로 옮긴다
    // (처리 기록 admin_actions에 HIDE·UNHIDE가 남아서 「왜 가렸었나」는 그대로 볼 수 있다)
    @Transactional
    public AdminReportActionResponse unhide(Long adminId, ReportTargetType type, Long targetId, String memo) {
        Target target = lockAndFind(type, targetId)
                .orElseThrow(() -> new CustomException(ReportErrorCode.TARGET_NOT_FOUND));
        if (target.deleted()) {
            throw new CustomException(ReportErrorCode.DELETED_TARGET);
        }
        if (target.hiddenReason() == null) {
            throw new CustomException(ReportErrorCode.NOT_HIDDEN);
        }
        if (type == ReportTargetType.POST) {
            postRepository.unhide(targetId);
        } else {
            commentRepository.unhide(targetId);
        }
        Instant now = clock.instant();
        int handled = reportRepository.moveStatus(type.name(), targetId,
                List.of(ReportStatus.HIDDEN.name()), ReportStatus.DISMISSED.name(), now);
        return record(adminId, AdminActionType.UNHIDE, type, targetId, null, memo, handled, now);
    }

    // 처리 기록을 남기고, 바뀐 뒤 상태를 DB에서 다시 읽어 돌려준다 (대상이 통째로 없으면 DELETED)
    private AdminReportActionResponse record(Long adminId, AdminActionType action, ReportTargetType type, Long targetId,
                                             ReportReason reason, String memo, int handled, Instant now) {
        actionRepository.save(AdminAction.of(adminId, action, type, targetId, reason, memo, now));
        String state = findTargetOrEmpty(type, targetId).map(Target::state).orElse("DELETED");
        return new AdminReportActionResponse(type.name(), String.valueOf(targetId), state, handled);
    }

    // 대상 줄을 잠근 뒤 읽는다. 줄이 없으면 empty
    private Optional<Target> lockAndFind(ReportTargetType type, Long targetId) {
        Optional<Long> locked = type == ReportTargetType.POST
                ? postRepository.lockForAdmin(targetId)
                : commentRepository.lockForAdmin(targetId);
        return locked.isEmpty() ? Optional.empty() : findTargetOrEmpty(type, targetId);
    }

    private List<Long> idsOf(List<ReportQueueRow> rows, ReportTargetType type) {
        return rows.stream().filter(r -> r.getTargetType().equals(type.name())).map(ReportQueueRow::getTargetId).toList();
    }

    private AdminReportItemResponse toItem(ReportQueueRow row, Map<Long, Post> posts, Map<Long, Comment> comments) {
        Target target = row.getTargetType().equals(ReportTargetType.POST.name())
                ? Target.of(posts.get(row.getTargetId()))
                : Target.of(comments.get(row.getTargetId()));
        List<String> reasons = Arrays.asList(row.getReasons().split(","));   // DB에서 이미 가나다순
        return new AdminReportItemResponse(row.getTargetType(), String.valueOf(row.getTargetId()),
                target == null ? null : String.valueOf(target.postId()),
                target == null ? null : target.preview(),
                target == null ? "DELETED" : target.state(),     // 글이 통째로 지워져 행이 없으면
                row.getReportCount(), row.getSystemReportCount(), reasons, row.getLastReportedAt());
    }

    // 운영자는 지운 글도 본다 (deleted_at이 있어도 찾음)
    private Target findTarget(ReportTargetType type, Long targetId) {
        return findTargetOrEmpty(type, targetId)
                .orElseThrow(() -> new CustomException(ReportErrorCode.TARGET_NOT_FOUND));
    }

    private Optional<Target> findTargetOrEmpty(ReportTargetType type, Long targetId) {
        return type == ReportTargetType.POST
                ? postRepository.findById(targetId).map(Target::of)
                : commentRepository.findById(targetId).map(Target::of);
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

        // 글자(코드 포인트) 40개까지. UTF-16 단위로 자르면 이모지 반쪽이 남아 깨진 글자가 나간다
        String preview() {
            String text = post != null ? post.getTitle() : comment.getContent();
            if (text.codePointCount(0, text.length()) <= PREVIEW_LENGTH) {
                return text;
            }
            return text.substring(0, text.offsetByCodePoints(0, PREVIEW_LENGTH)) + "…";
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

        Instant updatedAt() {
            return post != null ? post.getUpdatedAt() : comment.getUpdatedAt();
        }

        boolean deleted() {
            return (post != null ? post.getDeletedAt() : comment.getDeletedAt()) != null;
        }

        String hiddenReason() {
            return post != null ? post.getHiddenReason() : comment.getHiddenReason();
        }

        String state() {
            if (deleted()) {
                return "DELETED";
            }
            return hiddenReason() != null ? "HIDDEN" : "VISIBLE";
        }
    }
}
