package com.dangdang.domain.comment.service;

import com.dangdang.domain.comment.dto.request.CommentCreateRequest;
import com.dangdang.domain.comment.dto.request.CommentUpdateRequest;
import com.dangdang.domain.comment.dto.response.CommentListResponse;
import com.dangdang.domain.comment.dto.response.CommentResponse;
import com.dangdang.domain.comment.entity.Comment;
import com.dangdang.domain.comment.exception.CommentErrorCode;
import com.dangdang.domain.comment.repository.CommentReactionRepository;
import com.dangdang.domain.comment.repository.CommentRepository;
import com.dangdang.domain.community.entity.CommunityProfile;
import com.dangdang.domain.community.service.CommunityProfileService;
import com.dangdang.domain.post.dto.response.ReactionResponse;
import com.dangdang.domain.post.entity.Post;
import com.dangdang.domain.post.service.PostService;
import com.dangdang.global.common.IdCount;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommentReactionRepository reactionRepository;
    private final PostService postService;
    private final CommunityProfileService profileService;
    private final Clock clock;

    public CommentListResponse list(Long userId, Long postId) {
        postService.findVisiblePost(postId);
        Long myProfileId = profileService.findMyProfileId(userId).orElse(null);
        List<Comment> comments = commentRepository.findVisibleByPostId(postId);
        if (comments.isEmpty()) {
            return new CommentListResponse(List.of(), 0);
        }
        List<Long> ids = comments.stream().map(Comment::getId).toList();
        Map<Long, Long> counts = reactionRepository.countByCommentIds(ids).stream()
                .collect(Collectors.toMap(IdCount::getId, IdCount::getCnt));
        Set<Long> reacted = myProfileId == null ? Set.of()
                : reactionRepository.findReactedCommentIds(ids, myProfileId);
        List<CommentResponse> items = comments.stream()
                .map(c -> CommentResponse.of(c, myProfileId, counts.getOrDefault(c.getId(), 0L), reacted.contains(c.getId())))
                .toList();
        return new CommentListResponse(items, items.size());
    }

    @Transactional
    public CommentResponse create(Long userId, Long postId, CommentCreateRequest request) {
        CommunityProfile profile = profileService.requireProfile(userId);
        Post post = postService.findVisiblePost(postId);
        Comment parent = request.parentId() == null ? null : findParent(request.parentId(), postId);
        Comment comment = Comment.of(post, profile, parent, request.content().trim(),
                Boolean.TRUE.equals(request.isAnonymous()), clock.instant());
        commentRepository.save(comment);
        return CommentResponse.of(comment, profile.getId(), 0, false);
    }

    @Transactional
    public CommentResponse update(Long userId, Long commentId, CommentUpdateRequest request) {
        CommunityProfile profile = profileService.requireProfile(userId);
        Comment comment = findMine(commentId, profile.getId());
        if (comment.isHidden()) {   // 가려진 댓글을 고쳐서 다시 드러내는 것을 막는다
            throw new CustomException(CommentErrorCode.COMMENT_NOT_FOUND);
        }
        comment.edit(request.content().trim(), clock.instant());
        commentRepository.flush();
        return CommentResponse.of(comment, profile.getId(), reactionRepository.countByCommentId(commentId), false);
    }

    @Transactional
    public void delete(Long userId, Long commentId) {
        CommunityProfile profile = profileService.requireProfile(userId);
        findMine(commentId, profile.getId()).softDelete(clock.instant());
    }

    @Transactional
    public ReactionResponse addReaction(Long userId, Long commentId) {
        CommunityProfile profile = profileService.requireProfile(userId);
        findVisibleComment(commentId);
        reactionRepository.addIfAbsent(commentId, profile.getId(), clock.instant());
        return new ReactionResponse(true, reactionRepository.countByCommentId(commentId));
    }

    @Transactional
    public ReactionResponse removeReaction(Long userId, Long commentId) {
        CommunityProfile profile = profileService.requireProfile(userId);
        findVisibleComment(commentId);
        reactionRepository.remove(commentId, profile.getId());
        return new ReactionResponse(false, reactionRepository.countByCommentId(commentId));
    }

    // ★ 19단계 신고에서도 부른다 — 지운·가린 댓글, 글이 안 보이는 댓글은 없는 것으로
    public Comment findVisibleComment(Long commentId) {
        Comment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .filter(c -> !c.isHidden())
                .orElseThrow(() -> new CustomException(CommentErrorCode.COMMENT_NOT_FOUND));
        postService.findVisiblePost(comment.getPost().getId());
        return comment;
    }

    private Comment findParent(Long parentId, Long postId) {
        Comment parent = commentRepository.findByIdAndDeletedAtIsNull(parentId)
                .filter(c -> !c.isHidden() && c.getPost().getId().equals(postId))
                .orElseThrow(() -> new CustomException(CommentErrorCode.COMMENT_NOT_FOUND));
        if (parent.isReply()) {     // U-02b는 답글 아래에 답글 버튼이 없다 (1단계까지만)
            throw new CustomException(CommentErrorCode.REPLY_DEPTH_EXCEEDED);
        }
        return parent;
    }

    private Comment findMine(Long commentId, Long profileId) {
        return commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .filter(c -> c.isOwnedBy(profileId))     // 남의 댓글도 "없는 댓글"과 똑같이 404
                .orElseThrow(() -> new CustomException(CommentErrorCode.COMMENT_NOT_FOUND));
    }
}
