package com.dangdang.domain.post.service;

import com.dangdang.domain.comment.repository.CommentRepository;
import com.dangdang.domain.comment.service.BestComment;
import com.dangdang.domain.community.entity.CommunityProfile;
import com.dangdang.domain.community.service.BlockService;
import com.dangdang.domain.community.service.CommunityProfileService;
import com.dangdang.domain.community.service.SanctionService;
import com.dangdang.domain.community.service.WeekBadges;
import com.dangdang.domain.post.dto.request.PostCreateRequest;
import com.dangdang.domain.post.dto.request.PostUpdateRequest;
import com.dangdang.domain.post.dto.response.BestCommentResponse;
import com.dangdang.domain.post.dto.response.PollResponse;
import com.dangdang.domain.post.dto.response.PostDetailResponse;
import com.dangdang.domain.post.dto.response.PostListResponse;
import com.dangdang.domain.post.dto.response.PostSummaryResponse;
import com.dangdang.domain.post.entity.PollVote;
import com.dangdang.domain.post.entity.Post;
import com.dangdang.domain.post.enums.PopularPeriod;
import com.dangdang.domain.post.enums.PostAudience;
import com.dangdang.domain.post.enums.PostCategory;
import com.dangdang.domain.post.enums.PostSort;
import com.dangdang.domain.post.exception.PostErrorCode;
import com.dangdang.domain.post.infra.PrecheckKindStore;
import com.dangdang.domain.post.repository.PollVoteRepository;
import com.dangdang.domain.post.repository.PostDraftRepository;
import com.dangdang.domain.post.repository.PostPopularQuery;
import com.dangdang.domain.post.repository.PostReactionRepository;
import com.dangdang.domain.post.repository.PostRepository;
import com.dangdang.domain.post.repository.PostSpecifications;
import com.dangdang.domain.report.service.SystemReportService;
import com.dangdang.domain.user.enums.Role;
import com.dangdang.global.common.IdCount;
import com.dangdang.global.common.TimeCursor;
import com.dangdang.global.exception.CustomException;
import com.dangdang.global.exception.GlobalErrorCode;
import com.dangdang.global.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private static final Sort LATEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    static final int POPULAR_MAX = 50;          // 인기 게시판은 한 번에 50개 (size 무시, 다음 페이지 없음)
    private static final List<PostCategory> LATEST_ONLY_CATEGORIES =
            List.of(PostCategory.MEAL, PostCategory.VISIT, PostCategory.NEWS);

    private final PostRepository postRepository;
    private final PostPopularQuery popularQuery;
    private final PostDraftRepository draftRepository;
    private final PostReactionRepository reactionRepository;
    private final CommentRepository commentRepository;
    private final PollVoteRepository pollVoteRepository;
    private final PrecheckKindStore precheckKindStore;
    private final SystemReportService systemReportService;
    private final BlockService blockService;
    private final CommunityProfileService profileService;
    private final SanctionService sanctionService;
    private final WeekBadges weekBadges;
    private final Clock clock;

    public PostListResponse list(Long userId, PostCategory category, PostSort sort, PopularPeriod period,
                                 PostAudience who, String rawQ, String rawCursor, int size) {
        String q = normalizeSearch(rawQ);
        Long myProfileId = profileService.findMyProfileId(userId).orElse(null);
        if (sort == PostSort.POPULAR) {
            return popular(category, who, period, q, myProfileId);
        }
        String filter = category + "|" + q + "|" + who;
        TimeCursor cursor = rawCursor == null ? null
                : TimeCursor.decode(rawCursor, filter, PostErrorCode.INVALID_CURSOR);

        // size보다 하나 더 읽어서 다음 페이지가 있는지 본다
        List<Post> found = postRepository.findBy(
                PostSpecifications.feed(category, q, cursor, myProfileId, who),
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
        sanctionService.assertCanWrite(principal.userId());   // 이용 제한 중이면 403 (U-17)
        checkCategoryAllowed(request.category(), principal.role());
        Post post = Post.of(profile, request.category(), request.title().trim(), request.content().trim(),
                Boolean.TRUE.equals(request.isAnonymous()), request.poll(), clock.instant());
        postRepository.save(post);
        queueForReview(principal.userId(), post.getId(), request);
        draftRepository.deleteByProfileId(profile.getId());   // 올렸으면 임시 글은 끝
        return toDetail(post, profile.getId(), post.getViewCount());
    }

    // U-15b 「검토 중일 수 있어요」: 안내를 보고도 올린 글은 운영자 처리함에 (글은 막지 않는다)
    // 같은 사람이 같은 내용으로 올리기 전 확인을 했을 때 정말 걸렸던 종류만 인정하고, 나머지는 조용히 버린다
    private void queueForReview(Long userId, Long postId, PostCreateRequest request) {
        if (request.postedDespite() == null || request.postedDespite().isEmpty()) {
            return;
        }
        Set<String> checked = precheckKindStore.find(userId, request.title(), request.content());
        List<String> confirmed = request.postedDespite().stream().filter(checked::contains).distinct().toList();
        if (!confirmed.isEmpty()) {
            systemReportService.queuePostForReview(postId, confirmed);
        }
    }

    @Transactional
    public PostDetailResponse detail(Long userId, Long postId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new CustomException(PostErrorCode.POST_NOT_FOUND));
        Long myProfileId = profileService.findMyProfileId(userId).orElse(null);
        if (blockService.isBlocking(myProfileId, post.getProfile(), post.isAnonymous())) {
            throw new CustomException(PostErrorCode.POST_NOT_FOUND);
        }
        if (post.isHidden()) {
            return PostDetailResponse.hidden(post);
        }
        postRepository.increaseViewCount(postId);
        return toDetail(post, myProfileId, post.getViewCount() + 1);
    }

    @Transactional
    public PostDetailResponse update(AuthPrincipal principal, Long postId, PostUpdateRequest request) {
        if (request.category() == null && request.title() == null && request.content() == null) {
            throw new CustomException(PostErrorCode.EMPTY_UPDATE);
        }
        CommunityProfile profile = profileService.requireProfile(principal.userId());
        sanctionService.assertCanWrite(principal.userId());   // 이용 제한 중이면 고치기도 403 (U-17)
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

    // 댓글·공감·투표·신고에서 부른다 — 지운 글·가린 글·내가 안 보기 한 사람의 글은 없는 글로 친다
    public Post findVisiblePost(Long postId, Long myProfileId) {
        return postRepository.findByIdAndDeletedAtIsNull(postId)
                .filter(post -> !post.isHidden())
                .filter(post -> !blockService.isBlocking(myProfileId, post.getProfile(), post.isAnonymous()))
                .orElseThrow(() -> new CustomException(PostErrorCode.POST_NOT_FOUND));
    }

    // 투표 API도 같은 모양으로 돌려준다. 내가 투표하기 전에는 항목별 숫자를 숨긴다
    public PollResponse pollOf(Post post, Long myProfileId) {
        if (post.getPoll() == null) {
            return null;
        }
        Integer mine = myProfileId == null ? null
                : pollVoteRepository.findByPostIdAndProfileId(post.getId(), myProfileId)
                .map(PollVote::getOptionIndex).orElse(null);
        Map<Long, Long> counts = toMap(pollVoteRepository.countByOption(post.getId()));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return PollResponse.of(post.getPoll(), mine == null ? null : counts, total, mine);
    }

    private Post findMine(Long postId, Long profileId) {
        return postRepository.findByIdAndDeletedAtIsNull(postId)
                .filter(post -> post.isOwnedBy(profileId))      // 남의 글도 "없는 글"과 똑같이 404
                .orElseThrow(() -> new CustomException(PostErrorCode.POST_NOT_FOUND));
    }

    // 글마다 세지 않고 한 번에 모아 센다 (글 20개면 쿼리 40번 → 3번). 내 활동 목록(MyActivityService)도 쓴다
    public List<PostSummaryResponse> toSummaries(List<Post> posts, Long myProfileId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> ids = posts.stream().map(Post::getId).toList();
        Map<Long, Long> reactions = toMap(reactionRepository.countByPostIds(ids));
        Map<Long, Long> comments = toMap(commentRepository.countVisibleByPostIds(ids, viewerKey(myProfileId)));
        Set<Long> reacted = myProfileId == null ? Set.of()
                : reactionRepository.findReactedPostIds(ids, myProfileId);
        // 주수 배지: 글쓴이가 몇 명이어도 임신 표는 한 번 (익명 글은 배지를 안 붙이니 빼고 센다)
        Map<Long, String> badges = weekBadges.of(posts.stream()
                .filter(post -> !post.isAnonymous()).map(Post::getProfile).toList());
        return posts.stream()
                .map(post -> PostSummaryResponse.of(post, myProfileId,
                        reactions.getOrDefault(post.getId(), 0L),
                        comments.getOrDefault(post.getId(), 0L),
                        reacted.contains(post.getId()), badges))
                .toList();
    }

    private Map<Long, Long> toMap(List<IdCount> counts) {
        return counts.stream().collect(Collectors.toMap(IdCount::getId, IdCount::getCnt));
    }

    private PostDetailResponse toDetail(Post post, Long myProfileId, int viewCount) {
        Long postId = post.getId();
        long reactionCount = reactionRepository.countByPostId(postId);
        long commentCount = toMap(commentRepository.countVisibleByPostIds(List.of(postId), viewerKey(myProfileId)))
                .getOrDefault(postId, 0L);
        boolean reacted = myProfileId != null && reactionRepository.existsByPostIdAndProfileId(postId, myProfileId);
        Map<Long, String> badges = post.isAnonymous() || post.getProfile() == null ? Map.of()
                : weekBadges.of(List.of(post.getProfile()));
        return PostDetailResponse.of(post, myProfileId, viewCount, reactionCount, commentCount, reacted,
                pollOf(post, myProfileId), badges);
    }

    // 프로필이 없는 사람은 차단한 사람도 없다 → 절대 안 맞는 번호를 넘긴다
    private Long viewerKey(Long myProfileId) {
        return myProfileId == null ? -1L : myProfileId;
    }

    // 인기 게시판 (U-23~U-25): 기간 안 글을 공감+댓글 수로 DB에서 줄 세워 위 50개. 같으면 최신 먼저
    // 식단·진료 후기는 최신순만 (COM-13 — 비교·따라 하기 줄이기), 당당 소식(공식 카드)은 인기 경쟁 대상이 아니다
    private PostListResponse popular(PostCategory category, PostAudience who, PopularPeriod period, String q,
                                     Long myProfileId) {
        if (category != null && LATEST_ONLY_CATEGORIES.contains(category)) {
            throw new CustomException(PostErrorCode.LATEST_ONLY);
        }
        if (period == PopularPeriod.ALL && q == null) {   // 기간 없이 전체 글 점수를 매기지 않게 — 검색 결과에서만
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, "period=ALL은 검색어(q)와 같이 보내 주세요.");
        }
        Specification<Post> filter = PostSpecifications.feed(category, q, null, myProfileId, who)
                .and((root, query, cb) -> root.get("category").in(LATEST_ONLY_CATEGORIES).not());
        if (period.window() != null) {   // ALL(검색 결과 인기순, U-06)은 기간 조건 없이
            Instant since = clock.instant().minus(period.window());
            filter = filter.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), since));
        }
        List<Post> top = popularQuery.findTop(filter, viewerKey(myProfileId), POPULAR_MAX);
        return new PostListResponse(withBestComments(toSummaries(top, myProfileId), myProfileId), null, false);
    }

    // U-23 인기 카드 아래 베스트 댓글 — 글 50개여도 쿼리 2번 (글마다 댓글 API를 부르지 않게)
    private List<PostSummaryResponse> withBestComments(List<PostSummaryResponse> items, Long myProfileId) {
        if (items.isEmpty()) {
            return items;
        }
        List<Long> postIds = items.stream().map(i -> Long.valueOf(i.id())).toList();
        Map<Long, Long> counts = toMap(commentRepository.findBestComments(postIds, viewerKey(myProfileId),
                BestComment.MIN_REACTIONS));
        if (counts.isEmpty()) {
            return items;
        }
        Map<Long, BestCommentResponse> byPost = commentRepository.findWithProfileByIdIn(counts.keySet()).stream()
                .collect(Collectors.toMap(c -> c.getPost().getId(),
                        c -> BestCommentResponse.of(c, counts.get(c.getId()), myProfileId)));
        return items.stream().map(i -> i.withBestComment(byPost.get(Long.valueOf(i.id())))).toList();
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
