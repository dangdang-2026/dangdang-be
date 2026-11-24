package com.dangdang.domain.community.service;

import com.dangdang.domain.comment.entity.Comment;
import com.dangdang.domain.comment.repository.CommentRepository;
import com.dangdang.domain.community.dto.request.BlockRequest;
import com.dangdang.domain.community.dto.response.BlockListResponse;
import com.dangdang.domain.community.dto.response.BlockResponse;
import com.dangdang.domain.community.dto.response.BlockedProfileResponse;
import com.dangdang.domain.community.entity.CommunityProfile;
import com.dangdang.domain.community.entity.ProfileBlock;
import com.dangdang.domain.community.exception.CommunityErrorCode;
import com.dangdang.domain.community.repository.CommunityProfileRepository;
import com.dangdang.domain.community.repository.ProfileBlockRepository;
import com.dangdang.domain.post.entity.Post;
import com.dangdang.domain.post.repository.PostRepository;
import com.dangdang.global.common.TimeCursor;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlockService {

    private static final String CURSOR_FILTER = "blocks";

    private final ProfileBlockRepository blockRepository;
    private final CommunityProfileRepository profileRepository;
    private final CommunityProfileService profileService;
    // 서비스가 아니라 리포지토리를 쓴다 (PostService·CommentService가 BlockService를 불러서 서비스를 넣으면 순환)
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final Clock clock;

    // 다시 해도 200 (같은 줄은 DB UNIQUE + ON CONFLICT DO NOTHING이라 동시에 눌러도 한 줄)
    @Transactional
    public BlockResponse block(Long userId, BlockRequest request) {
        CommunityProfile me = profileService.requireProfile(userId);
        Target target = resolve(request);
        if (me.getId().equals(target.profileId())) {
            throw new CustomException(CommunityErrorCode.CANNOT_BLOCK_SELF);
        }
        blockRepository.addIfAbsent(me.getId(), target.profileId(), target.viaAnonymous(), clock.instant());
        return new BlockResponse(true);
    }

    private record Target(Long profileId, boolean viaAnonymous) {
    }

    // 글·댓글로 오면 서버가 글쓴이를 찾는다 → 익명이어도 프로필 번호가 화면에 나갈 일이 없다
    // 지운 글·댓글, 탈퇴한 글쓴이(profile null)는 없는 대상
    private Target resolve(BlockRequest request) {
        if (request.postId() != null) {
            Post post = postRepository.findByIdAndDeletedAtIsNull(request.postId())
                    .filter(p -> p.getProfile() != null)
                    .orElseThrow(() -> new CustomException(CommunityErrorCode.BLOCK_TARGET_NOT_FOUND));
            return new Target(post.getProfile().getId(), post.isAnonymous());
        }
        if (request.commentId() != null) {
            Comment comment = commentRepository.findByIdAndDeletedAtIsNull(request.commentId())
                    .filter(c -> c.getProfile() != null)
                    .orElseThrow(() -> new CustomException(CommunityErrorCode.BLOCK_TARGET_NOT_FOUND));
            return new Target(comment.getProfile().getId(), comment.isAnonymous());
        }
        if (!profileRepository.existsById(request.blockedProfileId())) {
            throw new CustomException(CommunityErrorCode.BLOCK_TARGET_NOT_FOUND);
        }
        return new Target(request.blockedProfileId(), false);
    }

    public BlockListResponse list(Long userId, String rawCursor, int size) {
        CommunityProfile me = profileService.requireProfile(userId);
        Limit limit = Limit.of(size + 1);
        List<ProfileBlock> found;
        if (rawCursor == null) {
            found = blockRepository.findFirstPage(me.getId(), limit);
        } else {
            TimeCursor cursor = TimeCursor.decode(rawCursor, CURSOR_FILTER, CommunityErrorCode.INVALID_CURSOR);
            found = blockRepository.findNextPage(me.getId(), cursor.at(), cursor.id(), limit);
        }
        boolean hasNext = found.size() > size;
        List<ProfileBlock> page = hasNext ? found.subList(0, size) : found;
        String nextCursor = null;
        if (hasNext) {
            ProfileBlock last = page.get(page.size() - 1);
            nextCursor = new TimeCursor(last.getCreatedAt(), last.getId()).encode(CURSOR_FILTER);
        }
        return new BlockListResponse(page.stream().map(BlockedProfileResponse::from).toList(), nextCursor, hasNext);
    }

    @Transactional
    public BlockResponse unblock(Long userId, Long blockedProfileId) {
        CommunityProfile me = profileService.requireProfile(userId);
        blockRepository.remove(me.getId(), blockedProfileId);
        return new BlockResponse(false);
    }

    // 목록 한 줄 풀기 (M-27). 「익명 사용자」 줄은 이걸로만. 없거나 남의 줄이어도 200 (위 해제와 같게)
    @Transactional
    public BlockResponse unblockEntry(Long userId, Long blockId) {
        CommunityProfile me = profileService.requireProfile(userId);
        blockRepository.removeById(blockId, me.getId());
        return new BlockResponse(false);
    }

    // 글·댓글·투표·신고가 부른다 — 내가 안 보기 한 사람인가
    public boolean isBlocking(Long myProfileId, CommunityProfile author) {
        return myProfileId != null && author != null
                && blockRepository.existsByProfileIdAndBlockedProfileId(myProfileId, author.getId());
    }
}
