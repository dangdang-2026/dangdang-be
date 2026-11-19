package com.dangdang.domain.community.service;

import com.dangdang.domain.community.dto.request.SanctionCreateRequest;
import com.dangdang.domain.community.dto.response.RestrictionResponse;
import com.dangdang.domain.community.dto.response.SanctionResponse;
import com.dangdang.domain.community.entity.UserSanction;
import com.dangdang.domain.community.exception.CommunityErrorCode;
import com.dangdang.domain.community.repository.UserSanctionRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

// 커뮤니티 이용 제한 (COM-09 · U-17). 반복 기준·기간 표는 [확정 필요] → 지금은 운영자가 직접 건다
@Slf4j
@Service
@RequiredArgsConstructor
public class SanctionService {

    private final UserSanctionRepository userSanctionRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    // 글·댓글 쓰기 전에 부른다
    @Transactional(readOnly = true)
    public void assertCanWrite(Long userId) {
        if (!userSanctionRepository.findActive(userId, clock.instant()).isEmpty()) {
            throw new CustomException(CommunityErrorCode.RESTRICTED);
        }
    }

    @Transactional(readOnly = true)
    public RestrictionResponse myRestriction(Long userId) {
        List<UserSanction> active = userSanctionRepository.findActive(userId, clock.instant());
        if (active.isEmpty()) {
            return RestrictionResponse.none();
        }
        UserSanction longest = active.get(0);
        return new RestrictionResponse(true, longest.getReason(), longest.getEndsAt());
    }

    @Transactional
    public SanctionResponse create(Long adminId, SanctionCreateRequest req) {
        User target = userRepository.findById(req.userId())
                .orElseThrow(() -> new CustomException(CommunityErrorCode.SANCTION_TARGET_NOT_FOUND));
        Instant now = clock.instant();
        Instant endsAt = req.days() == null ? null : now.plus(Duration.ofDays(req.days()));
        UserSanction saved = userSanctionRepository.save(
                UserSanction.of(target, req.reason().strip(), now, endsAt, adminId));
        log.info("[이용 제한] 걸림 - 대상: {}, 운영자: {}, 기간: {}일", target.getId(), adminId, req.days());
        return SanctionResponse.from(saved);
    }

    @Transactional
    public SanctionResponse lift(Long adminId, Long sanctionId) {
        UserSanction sanction = userSanctionRepository.findById(sanctionId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.SANCTION_NOT_FOUND));
        sanction.lift(clock.instant());
        log.info("[이용 제한] 풀림 - 제한: {}, 운영자: {}", sanctionId, adminId);
        return SanctionResponse.from(sanction);
    }
}
