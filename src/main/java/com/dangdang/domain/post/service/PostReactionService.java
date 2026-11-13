package com.dangdang.domain.post.service;

import com.dangdang.domain.community.entity.CommunityProfile;
import com.dangdang.domain.community.service.CommunityProfileService;
import com.dangdang.domain.post.dto.response.ReactionResponse;
import com.dangdang.domain.post.repository.PostReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class PostReactionService {

    private final PostReactionRepository reactionRepository;
    private final PostService postService;
    private final CommunityProfileService profileService;
    private final Clock clock;

    @Transactional
    public ReactionResponse add(Long userId, Long postId) {
        CommunityProfile profile = profileService.requireProfile(userId);
        postService.findVisiblePost(postId);
        reactionRepository.addIfAbsent(postId, profile.getId(), clock.instant());
        return new ReactionResponse(true, reactionRepository.countByPostId(postId));
    }

    @Transactional
    public ReactionResponse remove(Long userId, Long postId) {
        CommunityProfile profile = profileService.requireProfile(userId);
        postService.findVisiblePost(postId);
        reactionRepository.remove(postId, profile.getId());
        return new ReactionResponse(false, reactionRepository.countByPostId(postId));
    }
}
