package com.dangdang.domain.community.service;

import com.dangdang.domain.community.dto.request.CommunityProfileCreateRequest;
import com.dangdang.domain.community.dto.request.CommunityProfileUpdateRequest;
import com.dangdang.domain.community.dto.response.CommunityProfileResponse;
import com.dangdang.domain.community.dto.response.NicknameAvailabilityResponse;
import com.dangdang.domain.community.entity.CommunityProfile;
import com.dangdang.domain.community.exception.CommunityErrorCode;
import com.dangdang.domain.community.repository.CommunityProfileRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityProfileService {

    private final CommunityProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public NicknameAvailabilityResponse checkNickname(String rawNickname) {
        String nickname = NicknameRule.normalizeAndCheck(rawNickname);
        return new NicknameAvailabilityResponse(!profileRepository.existsByNickname(nickname));
    }

    @Transactional
    public CommunityProfileResponse create(Long userId, CommunityProfileCreateRequest request) {
        String nickname = NicknameRule.normalizeAndCheck(request.nickname());
        if (profileRepository.existsByUserId(userId)) {
            throw new CustomException(CommunityErrorCode.PROFILE_ALREADY_EXISTS);
        }
        if (profileRepository.existsByNickname(nickname)) {
            throw new CustomException(CommunityErrorCode.NICKNAME_DUPLICATED);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        boolean showWeekBadge = Boolean.TRUE.equals(request.showWeekBadge());
        CommunityProfile profile = CommunityProfile.of(user, nickname, showWeekBadge, clock.instant());
        try {
            profileRepository.saveAndFlush(profile); // 같은 이름이 동시에 오면 여기서 DB UNIQUE가 막음
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(CommunityErrorCode.NICKNAME_DUPLICATED);
        }
        return CommunityProfileResponse.from(profile);
    }

    public CommunityProfileResponse getMine(Long userId) {
        return CommunityProfileResponse.from(requireProfile(userId));
    }

    @Transactional
    public CommunityProfileResponse update(Long userId, CommunityProfileUpdateRequest request) {
        if (request.nickname() == null && request.showWeekBadge() == null) {
            throw new CustomException(CommunityErrorCode.EMPTY_PROFILE_UPDATE);
        }
        CommunityProfile profile = requireProfile(userId);
        String nickname = request.nickname() == null ? null : NicknameRule.normalizeAndCheck(request.nickname());
        if (nickname != null && profileRepository.existsByNicknameAndIdNot(nickname, profile.getId())) {
            throw new CustomException(CommunityErrorCode.NICKNAME_DUPLICATED);
        }
        profile.update(nickname, request.showWeekBadge(), clock.instant());
        try {
            profileRepository.saveAndFlush(profile);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(CommunityErrorCode.NICKNAME_DUPLICATED);
        }
        return CommunityProfileResponse.from(profile);
    }

    // ★ 글·댓글·차단 가이드에서 부른다 — 쓰는 기능은 프로필이 꼭 있어야 한다
    public CommunityProfile requireProfile(Long userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.PROFILE_NOT_FOUND));
    }

    // ★ 읽는 기능은 프로필이 없어도 된다 (U-04가 뜨기 전에도 목록은 보임)
    public Optional<Long> findMyProfileId(Long userId) {
        return profileRepository.findByUserId(userId).map(CommunityProfile::getId);
    }
}
