package com.dangdang.domain.post.service;

import com.dangdang.domain.comment.dto.response.MyCommentListResponse;
import com.dangdang.domain.comment.dto.response.MyCommentResponse;
import com.dangdang.domain.comment.entity.Comment;
import com.dangdang.domain.comment.repository.CommentReactionRepository;
import com.dangdang.domain.comment.repository.CommentRepository;
import com.dangdang.domain.community.repository.ProfileBlockRepository;
import com.dangdang.domain.community.service.CommunityProfileService;
import com.dangdang.domain.post.dto.response.MyCommunityCountsResponse;
import com.dangdang.domain.post.dto.response.PostListResponse;
import com.dangdang.domain.post.entity.Post;
import com.dangdang.domain.post.entity.PostReaction;
import com.dangdang.domain.post.exception.PostErrorCode;
import com.dangdang.domain.post.repository.PostReactionRepository;
import com.dangdang.domain.post.repository.PostRepository;
import com.dangdang.domain.post.repository.PostSpecifications;
import com.dangdang.global.common.IdCount;
import com.dangdang.global.common.TimeCursor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 마이 › 커뮤니티 활동 — 내가 쓴 글(M-24) · 내가 쓴 댓글(M-25) · 공감한 글(M-26) · 개수(M-14)
// 목록은 커서 페이지, 최신 먼저. 커뮤니티 프로필이 없으면 활동도 없다 → 빈 목록 · 0개 (404 아님)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyActivityService {

    private static final Sort LATEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    // 커서마다 다른 이름 → 다른 목록의 커서를 넣으면 decode가 400으로 막는다
    private static final String POSTS = "me-posts";
    private static final String COMMENTS = "me-comments";
    private static final String REACTIONS = "me-reactions";

    private final CommunityProfileService profileService;
    private final PostService postService;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final CommentReactionRepository commentReactionRepository;
    private final PostReactionRepository reactionRepository;
    private final ProfileBlockRepository blockRepository;

    // 내가 쓴 글 (M-24) — 지운 글·가린 글은 뺀다. 글 목록(GET /api/posts)과 같은 모양
    public PostListResponse myPosts(Long userId, String rawCursor, int size) {
        Long me = profileService.findMyProfileId(userId).orElse(null);
        if (me == null) {
            return new PostListResponse(List.of(), null, false);
        }
        TimeCursor cursor = decode(rawCursor, POSTS);
        List<Post> found = postRepository.findBy(myPosts(me, cursor),
                query -> query.sortBy(LATEST).limit(size + 1).all());
        Page<Post> page = Page.of(found, size, post -> new TimeCursor(post.getCreatedAt(), post.getId()), POSTS);
        return new PostListResponse(postService.toSummaries(page.items(), me), page.nextCursor(), page.hasNext());
    }

    // 내가 쓴 댓글 (M-25) — 댓글 + 원글 번호·제목 + 공감 수
    public MyCommentListResponse myComments(Long userId, String rawCursor, int size) {
        Long me = profileService.findMyProfileId(userId).orElse(null);
        if (me == null) {
            return new MyCommentListResponse(List.of(), null, false);
        }
        TimeCursor cursor = decode(rawCursor, COMMENTS);
        List<Comment> found = cursor == null
                ? commentRepository.findMine(me, Limit.of(size + 1))
                : commentRepository.findMineBefore(me, cursor.at(), cursor.id(), Limit.of(size + 1));
        Page<Comment> page = Page.of(found, size, c -> new TimeCursor(c.getCreatedAt(), c.getId()), COMMENTS);

        // 댓글마다 세지 않고 한 번에 센다 (20개면 쿼리 20번 → 1번)
        List<Long> ids = page.items().stream().map(Comment::getId).toList();
        Map<Long, Long> reactions = ids.isEmpty() ? Map.of()
                : commentReactionRepository.countByCommentIds(ids).stream()
                .collect(Collectors.toMap(IdCount::getId, IdCount::getCnt));
        List<MyCommentResponse> items = page.items().stream()
                .map(c -> MyCommentResponse.of(c, reactions.getOrDefault(c.getId(), 0L)))
                .toList();
        return new MyCommentListResponse(items, page.nextCursor(), page.hasNext());
    }

    // 공감한 글 (M-26) — 공감을 누른 때가 최신인 것 먼저. 커서도 공감 줄(시각, id) 기준
    public PostListResponse myReactions(Long userId, String rawCursor, int size) {
        Long me = profileService.findMyProfileId(userId).orElse(null);
        if (me == null) {
            return new PostListResponse(List.of(), null, false);
        }
        TimeCursor cursor = decode(rawCursor, REACTIONS);
        List<PostReaction> found = cursor == null
                ? reactionRepository.findMine(me, Limit.of(size + 1))
                : reactionRepository.findMineBefore(me, cursor.at(), cursor.id(), Limit.of(size + 1));
        Page<PostReaction> page = Page.of(found, size, r -> new TimeCursor(r.getCreatedAt(), r.getId()), REACTIONS);
        List<Post> posts = page.items().stream().map(PostReaction::getPost).toList();
        return new PostListResponse(postService.toSummaries(posts, me), page.nextCursor(), page.hasNext());
    }

    // 커뮤니티 설정 (M-14) 개수 4개 — 위 세 목록과 「안 보는 사람」 목록과 같은 조건으로 센다
    public MyCommunityCountsResponse counts(Long userId) {
        Long me = profileService.findMyProfileId(userId).orElse(null);
        if (me == null) {
            return MyCommunityCountsResponse.empty();
        }
        return new MyCommunityCountsResponse(
                postRepository.count(myPosts(me, null)),
                commentRepository.countMine(me),
                reactionRepository.countMine(me),
                blockRepository.countByProfileId(me));
    }

    // 글 목록 조건(지운 글·가린 글 빼기 + 커서)을 그대로 쓰고, 글쓴이가 나인 것만
    private Specification<Post> myPosts(Long me, TimeCursor cursor) {
        return PostSpecifications.feed(null, null, cursor, null)
                .and((root, query, cb) -> cb.equal(root.get("profile").get("id"), me));
    }

    private TimeCursor decode(String raw, String filter) {
        return raw == null ? null : TimeCursor.decode(raw, filter, PostErrorCode.INVALID_CURSOR);
    }

    // size+1개 읽은 것 → 이번 페이지 + 다음 커서
    private record Page<T>(List<T> items, String nextCursor, boolean hasNext) {
        static <T> Page<T> of(List<T> found, int size, Function<T, TimeCursor> cursorOf, String filter) {
            boolean hasNext = found.size() > size;
            List<T> items = hasNext ? found.subList(0, size) : found;
            String next = hasNext ? cursorOf.apply(items.get(items.size() - 1)).encode(filter) : null;
            return new Page<>(items, next, hasNext);
        }
    }
}
