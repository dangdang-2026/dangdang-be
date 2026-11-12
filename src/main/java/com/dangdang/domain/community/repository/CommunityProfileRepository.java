package com.dangdang.domain.community.repository;

import com.dangdang.domain.community.entity.CommunityProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommunityProfileRepository extends JpaRepository<CommunityProfile, Long> {

    Optional<CommunityProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    boolean existsByNickname(String nickname);

    // 수정할 때: 나를 뺀 다른 사람이 이 이름을 쓰는지
    boolean existsByNicknameAndIdNot(String nickname, Long id);
}
