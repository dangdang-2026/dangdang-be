package com.dangdang.domain.post.service;

import com.dangdang.domain.community.entity.CommunityProfile;
import com.dangdang.domain.community.service.CommunityProfileService;
import com.dangdang.domain.post.dto.request.PostDraftRequest;
import com.dangdang.domain.post.dto.response.PostDraftListResponse;
import com.dangdang.domain.post.dto.response.PostDraftResponse;
import com.dangdang.domain.post.entity.PostDraft;
import com.dangdang.domain.post.repository.PostDraftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostDraftService {

    private final PostDraftRepository draftRepository;
    private final CommunityProfileService profileService;
    private final Clock clock;

    // 있으면 덮어쓰고 없으면 새로 만든다 (U-16)
    @Transactional
    public PostDraftResponse save(Long userId, PostDraftRequest request) {
        CommunityProfile profile = profileService.requireProfile(userId);
        PostDraft draft = draftRepository.findByProfileId(profile.getId())
                .orElseGet(() -> PostDraft.empty(profile));
        draft.overwrite(request.category(), request.title(), request.content(),
                Boolean.TRUE.equals(request.isAnonymous()), request.poll(), clock.instant());
        return PostDraftResponse.from(draftRepository.saveAndFlush(draft));
    }

    public PostDraftListResponse getMine(Long userId) {
        CommunityProfile profile = profileService.requireProfile(userId);
        List<PostDraftResponse> items = draftRepository.findByProfileId(profile.getId())
                .map(PostDraftResponse::from)
                .stream()
                .toList();
        return PostDraftListResponse.of(items);
    }
}
