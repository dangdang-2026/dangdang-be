package com.dangdang.domain.community.service;

import com.dangdang.domain.community.dto.request.BlockRequest;
import com.dangdang.domain.community.dto.response.BlockListResponse;
import com.dangdang.domain.community.dto.response.BlockResponse;
import com.dangdang.domain.community.dto.response.BlockedProfileResponse;
import com.dangdang.domain.community.entity.CommunityProfile;
import com.dangdang.domain.community.entity.ProfileBlock;
import com.dangdang.domain.community.exception.CommunityErrorCode;
import com.dangdang.domain.community.repository.CommunityProfileRepository;
import com.dangdang.domain.community.repository.ProfileBlockRepository;
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
    private final Clock clock;

    @Transactional
    public BlockResponse block(Long userId, BlockRequest request) {
        CommunityProfile me = profileService.requireProfile(userId);
        Long targetId = request.blockedProfileId();
        if (me.getId().equals(targetId)) {
            throw new CustomException(CommunityErrorCode.CANNOT_BLOCK_SELF);
        }
        if (!profileRepository.existsById(targetId)) {
            throw new CustomException(CommunityErrorCode.BLOCK_TARGET_NOT_FOUND);
        }
        blockRepository.addIfAbsent(me.getId(), targetId, clock.instant());
        return new BlockResponse(true);
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

    // 글·댓글·투표·신고가 부른다 — 내가 안 보기 한 사람인가
    public boolean isBlocking(Long myProfileId, CommunityProfile author) {
        return myProfileId != null && author != null
                && blockRepository.existsByProfileIdAndBlockedProfileId(myProfileId, author.getId());
    }
}
