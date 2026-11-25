package com.dangdang.domain.community.repository;

import com.dangdang.domain.community.entity.ProfileBlock;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ProfileBlockRepository extends JpaRepository<ProfileBlock, Long> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO profile_blocks (profile_id, blocked_profile_id, via_anonymous, created_at)
            VALUES (:profileId, :blockedProfileId, :viaAnonymous, :now)
            ON CONFLICT (profile_id, blocked_profile_id, via_anonymous) DO NOTHING
            """)
    void addIfAbsent(@Param("profileId") Long profileId, @Param("blockedProfileId") Long blockedProfileId,
                     @Param("viaAnonymous") boolean viaAnonymous, @Param("now") Instant now);

    // 이름으로 막은 줄만 푼다. 익명 글·댓글로 막은 줄은 목록의 blockId로 removeById
    @Modifying
    @Query("""
            delete from ProfileBlock b
            where b.profile.id = :profileId and b.blockedProfile.id = :blockedProfileId and b.viaAnonymous = false
            """)
    void remove(@Param("profileId") Long profileId, @Param("blockedProfileId") Long blockedProfileId);

    // 목록 한 줄 풀기 (M-27). 내 줄만 지운다 (남의 줄 번호면 0줄)
    @Modifying
    @Query("delete from ProfileBlock b where b.id = :blockId and b.profile.id = :profileId")
    void removeById(@Param("blockId") Long blockId, @Param("profileId") Long profileId);

    boolean existsByProfileIdAndBlockedProfileIdAndViaAnonymous(Long profileId, Long blockedProfileId,
                                                                boolean viaAnonymous);

    // M-14 「안 보는 사람 N명」
    long countByProfileId(Long profileId);

    @Query("""
            select b from ProfileBlock b join fetch b.blockedProfile
            where b.profile.id = :profileId
            order by b.createdAt desc, b.id desc
            """)
    List<ProfileBlock> findFirstPage(@Param("profileId") Long profileId, Limit limit);

    @Query("""
            select b from ProfileBlock b join fetch b.blockedProfile
            where b.profile.id = :profileId
              and (b.createdAt < :at or (b.createdAt = :at and b.id < :id))
            order by b.createdAt desc, b.id desc
            """)
    List<ProfileBlock> findNextPage(@Param("profileId") Long profileId, @Param("at") Instant at,
                                    @Param("id") Long id, Limit limit);
}
