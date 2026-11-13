package com.dangdang.domain.post.service;

import com.dangdang.domain.community.entity.CommunityProfile;
import com.dangdang.domain.community.service.CommunityProfileService;
import com.dangdang.domain.post.dto.request.PollVoteRequest;
import com.dangdang.domain.post.dto.response.PollResponse;
import com.dangdang.domain.post.entity.Post;
import com.dangdang.domain.post.exception.PostErrorCode;
import com.dangdang.domain.post.repository.PollVoteRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class PollVoteService {

    private final PollVoteRepository voteRepository;
    private final PostService postService;
    private final CommunityProfileService profileService;
    private final Clock clock;

    @Transactional
    public PollResponse vote(Long userId, Long postId, PollVoteRequest request) {
        CommunityProfile profile = profileService.requireProfile(userId);
        Post post = postService.findVisiblePost(postId, profile.getId());
        if (post.getPoll() == null) {
            throw new CustomException(PostErrorCode.POLL_NOT_FOUND);
        }
        int option = request.optionIndex();
        if (option >= post.getPoll().size()) {
            throw new CustomException(PostErrorCode.INVALID_POLL_OPTION);
        }
        voteRepository.addIfAbsent(postId, profile.getId(), option, clock.instant());
        // 이미 투표했으면 위 줄이 아무 일도 안 한다 → 저장된 값이 내 선택과 다르면 409
        int saved = voteRepository.findByPostIdAndProfileId(postId, profile.getId())
                .orElseThrow(() -> new CustomException(PostErrorCode.POLL_NOT_FOUND))
                .getOptionIndex();
        if (saved != option) {
            throw new CustomException(PostErrorCode.VOTE_ALREADY_CAST);
        }
        return postService.pollOf(post, profile.getId());
    }
}
