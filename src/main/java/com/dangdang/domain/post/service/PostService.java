package com.dangdang.domain.post.service;

import com.dangdang.domain.comment.repository.CommentRepository;
import com.dangdang.domain.community.entity.CommunityProfile;
import com.dangdang.domain.community.service.CommunityProfileService;
import com.dangdang.domain.post.dto.request.PostCreateRequest;
import com.dangdang.domain.post.dto.request.PostUpdateRequest;
import com.dangdang.domain.post.dto.response.PollResponse;
import com.dangdang.domain.post.dto.response.PostDetailResponse;
import com.dangdang.domain.post.dto.response.PostListResponse;
import com.dangdang.domain.post.dto.response.PostSummaryResponse;
import com.dangdang.domain.post.entity.Post;
import com.dangdang.domain.post.enums.PostCategory;
import com.dangdang.domain.post.exception.PostErrorCode;
import com.dangdang.domain.post.repository.PostDraftRepository;
import com.dangdang.domain.post.repository.PostReactionRepository;
import com.dangdang.domain.post.repository.PostRepository;
import com.dangdang.domain.post.repository.PostSpecifications;
import com.dangdang.domain.user.enums.Role;
import com.dangdang.global.common.IdCount;
import com.dangdang.global.common.TimeCursor;
import com.dangdang.global.exception.CustomException;
import com.dangdang.global.exception.GlobalErrorCode;
import com.dangdang.global.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
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
public class PostService {

    private static final Sort LATEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final PostRepository postRepository;
    private final PostDraftRepository draftRepository;
    private final PostReactionRepository reactionRepository;
    private final CommentRepository commentRepository;
    private final CommunityProfileService profileService;
    private final Clock clock;

    public PostListResponse list(Long userId, PostCategory category, String sort, String period, String audience,
                                 String rawQ, String rawCursor, int size) {
        rejectDeferredFilters(sort, period, audience);
        String q = normalizeSearch(rawQ);
        String filter = category + "|" + q;
        TimeCursor cursor = rawCursor == null ? null
                : TimeCursor.decode(rawCursor, filter, PostErrorCode.INVALID_CURSOR);
        Long myProfileId = profileService.findMyProfileId(userId).orElse(null);

        // size보다 하나 더 읽어서 다음 페이지가 있는지 본다
        List<Post> found = postRepository.findBy(
                PostSpecifications.feed(category, q, cursor),
                query -> query.sortBy(LATEST).limit(size + 1).all());
        boolean hasNext = found.size() > size;
        List<Post> page = hasNext ? found.subList(0, size) : found;

        List<PostSummaryResponse> items = toSummaries(page, myProfileId);
        String nextCursor = null;
        if (hasNext) {
            Post last = page.get(page.size() - 1);
            nextCursor = new TimeCursor(last.getCreatedAt(), last.getId()).encode(filter);
        }
        return new PostListResponse(items, nextCursor, hasNext);
    }

    @Transactional
    public PostDetailResponse create(AuthPrincipal principal, PostCreateRequest request) {
        CommunityProfile profile = profileService.requireProfile(principal.userId());
        checkCategoryAllowed(request.category(), principal.role());
        Post post = Post.of(profile, request.category(), request.title().trim(), request.content().trim(),
                Boolean.TRUE.equals(request.isAnonymous()), request.poll(), clock.instant());
        postRepository.save(post);
        draftRepository.deleteByProfileId(profile.getId());   // 올렸으면 임시 글은 끝
        return toDetail(post, profile.getId(), post.getViewCount());
    }

    @Transactional
    public PostDetailResponse detail(Long userId, Long postId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new CustomException(PostErrorCode.POST_NOT_FOUND));
        if (post.isHidden()) {
            return PostDetailResponse.hidden(post);
        }
        Long myProfileId = profileService.findMyProfileId(userId).orElse(null);
        postRepository.increaseViewCount(postId);
        return toDetail(post, myProfileId, post.getViewCount() + 1);
    }

    @Transactional
    public PostDetailResponse update(AuthPrincipal principal, Long postId, PostUpdateRequest request) {
        if (request.category() == null && request.title() == null && request.content() == null) {
            throw new CustomException(PostErrorCode.EMPTY_UPDATE);
        }
        CommunityProfile profile = profileService.requireProfile(principal.userId());
        Post post = findMine(postId, profile.getId());
        if (post.isHidden()) {   // 가려진 글을 고쳐서 다시 드러내는 것을 막는다
            throw new CustomException(PostErrorCode.POST_NOT_FOUND);
        }
        checkCategoryAllowed(request.category(), principal.role());
        post.update(request.category(), trimOrReject(request.title()), trimOrReject(request.content()), clock.instant());
        postRepository.flush();
        return toDetail(post, profile.getId(), post.getViewCount());
    }

    @Transactional
    public void delete(Long userId, Long postId) {
        CommunityProfile profile = profileService.requireProfile(userId);
        findMine(postId, profile.getId()).softDelete(clock.instant());
    }

    // ★ 댓글·공감에서 부른다 — 지운 글·가린 글은 없는 글로 친다
    public Post findVisiblePost(Long postId) {
        return postRepository.findByIdAndDeletedAtIsNull(postId)
                .filter(post -> !post.isHidden())
                .orElseThrow(() -> new CustomException(PostErrorCode.POST_NOT_FOUND));
    }

    private Post findMine(Long postId, Long profileId) {
        return postRepository.findByIdAndDeletedAtIsNull(postId)
                .filter(post -> post.isOwnedBy(profileId))      // 남의 글도 "없는 글"과 똑같이 404
                .orElseThrow(() -> new CustomException(PostErrorCode.POST_NOT_FOUND));
    }

    // 글마다 세지 않고 한 번에 모아 센다 (글 20개면 쿼리 40번 → 3번)
    private List<PostSummaryResponse> toSummaries(List<Post> posts, Long myProfileId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> ids = posts.stream().map(Post::getId).toList();
        Map<Long, Long> reactions = toMap(reactionRepository.countByPostIds(ids));
        Map<Long, Long> comments = toMap(commentRepository.countVisibleByPostIds(ids));
        Set<Long> reacted = myProfileId == null ? Set.of()
                : reactionRepository.findReactedPostIds(ids, myProfileId);
        return posts.stream()
                .map(post -> PostSummaryResponse.of(post, myProfileId,
                        reactions.getOrDefault(post.getId(), 0L),
                        comments.getOrDefault(post.getId(), 0L),
                        reacted.contains(post.getId())))
                .toList();
    }

    private Map<Long, Long> toMap(List<IdCount> counts) {
        return counts.stream().collect(Collectors.toMap(IdCount::getId, IdCount::getCnt));
    }

    private PostDetailResponse toDetail(Post post, Long myProfileId, int viewCount) {
        Long postId = post.getId();
        long reactionCount = reactionRepository.countByPostId(postId);
        long commentCount = toMap(commentRepository.countVisibleByPostIds(List.of(postId))).getOrDefault(postId, 0L);
        boolean reacted = myProfileId != null && reactionRepository.existsByPostIdAndProfileId(postId, myProfileId);
        PollResponse poll = post.getPoll() == null ? null : PollResponse.notVoted(post.getPoll());
        return PostDetailResponse.of(post, myProfileId, viewCount, reactionCount, commentCount, reacted, poll);
    }

    private void rejectDeferredFilters(String sort, String period, String audience) {
        if (period != null) {
            throw new CustomException(PostErrorCode.DEFERRED_FILTER);
        }
        if (sort != null && !"LATEST".equals(sort)) {
            throw "POPULAR".equals(sort) ? new CustomException(PostErrorCode.DEFERRED_FILTER)
                    : new CustomException(GlobalErrorCode.INVALID_INPUT);
        }
        if (audience != null && !"ALL".equals(audience)) {
            throw "PREGNANT".equals(audience) || "POSTPARTUM".equals(audience)
                    ? new CustomException(PostErrorCode.DEFERRED_FILTER)
                    : new CustomException(GlobalErrorCode.INVALID_INPUT);
        }
    }

    private String normalizeSearch(String rawQ) {
        if (rawQ == null) {
            return null;
        }
        String q = rawQ.trim();
        if (q.isEmpty()) {
            throw new CustomException(PostErrorCode.INVALID_SEARCH);
        }
        return q;
    }

    private void checkCategoryAllowed(PostCategory category, Role role) {
        if (category == PostCategory.NEWS && role != Role.ADMIN) {
            throw new CustomException(PostErrorCode.OFFICIAL_ONLY);
        }
    }

    private String trimOrReject(String text) {
        if (text == null) {
            return null;
        }
        if (text.isBlank()) {
            throw new CustomException(PostErrorCode.BLANK_TEXT);
        }
        return text.trim();
    }
}
