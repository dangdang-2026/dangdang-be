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
import com.dangdang.domain.community.service.BlockService;
import com.dangdang.domain.community.service.CommunityProfileService;
import com.dangdang.domain.community.service.SanctionService;
import com.dangdang.domain.community.service.WeekBadges;
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
    private final SanctionService sanctionService;
    private final BlockService blockService;
    private final WeekBadges weekBadges;
    private final Clock clock;

    public CommentListResponse list(Long userId, Long postId) {
        Long myProfileId = profileService.findMyProfileId(userId).orElse(null);
        postService.findVisiblePost(postId, myProfileId);
        Long viewer = myProfileId == null ? -1L : myProfileId;  // 프로필이 없으면 차단한 사람도 없다
        List<Comment> comments = commentRepository.findVisibleByPostId(postId, viewer);
        if (comments.isEmpty()) {
            return new CommentListResponse(List.of(), 0);
        }
        List<Long> ids = comments.stream().map(Comment::getId).toList();
        Map<Long, Long> counts = reactionRepository.countByCommentIds(ids).stream()
                .collect(Collectors.toMap(IdCount::getId, IdCount::getCnt));
        Set<Long> reacted = myProfileId == null ? Set.of()
                : reactionRepository.findReactedCommentIds(ids, myProfileId);
        // U-02b 「Best」는 지금 보이는 댓글 중에서 고른다 (지운·가린 댓글, 안 보기 한 사람 댓글은 이미 빠져 있다)
        Long bestId = BestComment.pick(comments, counts);
        Map<Long, String> badges = weekBadges.of(comments.stream()
                .filter(c -> !c.isAnonymous()).map(Comment::getProfile).toList());   // 임신 표는 한 번만
        List<CommentResponse> items = comments.stream()
                .map(c -> CommentResponse.of(c, myProfileId, counts.getOrDefault(c.getId(), 0L),
                        reacted.contains(c.getId()), c.getId().equals(bestId), badges))
                .toList();
        return new CommentListResponse(items, items.size());
    }

    @Transactional
    public CommentResponse create(Long userId, Long postId, CommentCreateRequest request) {
        CommunityProfile profile = profileService.requireProfile(userId);
        sanctionService.assertCanWrite(userId);   // 이용 제한 중이면 403 (U-17)
        Post post = postService.findVisiblePost(postId, profile.getId());
        Comment parent = request.parentId() == null ? null : findParent(request.parentId(), postId, profile.getId());
        boolean byAuthor = post.isOwnedBy(profile.getId());
        // U-18: 익명 글의 글쓴이가 자기 글에 다는 댓글은 보낸 값과 상관없이 익명 (이름으로 달면 익명 글쓴이가 드러난다)
        boolean anonymous = Boolean.TRUE.equals(request.isAnonymous()) || (post.isAnonymous() && byAuthor);
        // 익명 글의 글쓴이는 번호 대신 「글쓴이」 라벨
        Integer anonymousNo = anonymous && !(post.isAnonymous() && byAuthor)
                ? anonymousNoFor(postId, profile.getId()) : null;
        Comment comment = Comment.of(post, profile, parent, request.content().trim(), anonymous, anonymousNo,
                clock.instant());
        commentRepository.save(comment);
        return CommentResponse.of(comment, profile.getId(), 0, false, false, badgesOf(comment));
    }

    @Transactional
    public CommentResponse update(Long userId, Long commentId, CommentUpdateRequest request) {
        CommunityProfile profile = profileService.requireProfile(userId);
        sanctionService.assertCanWrite(userId);   // 이용 제한 중이면 고치기도 403 (U-17)
        Comment comment = findMine(commentId, profile.getId());
        if (comment.isHidden()) {   // 가려진 댓글을 고쳐서 다시 드러내는 것을 막는다
            throw new CustomException(CommentErrorCode.COMMENT_NOT_FOUND);
        }
        comment.edit(request.content().trim(), clock.instant());
        commentRepository.flush();
        return CommentResponse.of(comment, profile.getId(), reactionRepository.countByCommentId(commentId), false,
                false, badgesOf(comment));
    }

    @Transactional
    public void delete(Long userId, Long commentId) {
        CommunityProfile profile = profileService.requireProfile(userId);
        findMine(commentId, profile.getId()).softDelete(clock.instant());
    }

    @Transactional
    public ReactionResponse addReaction(Long userId, Long commentId) {
        CommunityProfile profile = profileService.requireProfile(userId);
        findVisibleComment(commentId, profile.getId());
        reactionRepository.addIfAbsent(commentId, profile.getId(), clock.instant());
        return new ReactionResponse(true, reactionRepository.countByCommentId(commentId));
    }

    @Transactional
    public ReactionResponse removeReaction(Long userId, Long commentId) {
        CommunityProfile profile = profileService.requireProfile(userId);
        findVisibleComment(commentId, profile.getId());
        reactionRepository.remove(commentId, profile.getId());
        return new ReactionResponse(false, reactionRepository.countByCommentId(commentId));
    }

    // 신고에서도 부른다 — 지운·가린 댓글, 안 보기 한 사람의 댓글, 글이 안 보이는 댓글은 없는 것으로
    public Comment findVisibleComment(Long commentId, Long myProfileId) {
        Comment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .filter(c -> !c.isHidden())
                .filter(c -> !blockService.isBlocking(myProfileId, c.getProfile()))
                .orElseThrow(() -> new CustomException(CommentErrorCode.COMMENT_NOT_FOUND));
        postService.findVisiblePost(comment.getPost().getId(), myProfileId);
        return comment;
    }

    // U-02b 「익명 1·2」: 이 글에서 처음 익명 댓글을 단 순서. 같은 사람은 늘 같은 번호
    // 글마다 차례로 정해서(advisory lock, 트랜잭션 끝까지) 동시에 첫 댓글을 달아도 번호가 겹치지 않는다
    private int anonymousNoFor(Long postId, Long profileId) {
        commentRepository.lockAnonymousNumbers(postId);
        return commentRepository.findAnonymousNo(postId, profileId)
                .orElseGet(() -> commentRepository.nextAnonymousNo(postId));
    }

    // 쓰기·고치기 응답 한 개짜리 — 익명 댓글이면 배지가 안 붙으니 임신 표를 읽지 않는다
    private Map<Long, String> badgesOf(Comment comment) {
        return comment.isAnonymous() ? Map.of() : weekBadges.of(List.of(comment.getProfile()));
    }

    private Comment findParent(Long parentId, Long postId, Long myProfileId) {
        Comment parent = commentRepository.findByIdAndDeletedAtIsNull(parentId)
                .filter(c -> !c.isHidden() && c.getPost().getId().equals(postId))
                .filter(c -> !blockService.isBlocking(myProfileId, c.getProfile()))
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
