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
            INSERT INTO profile_blocks (profile_id, blocked_profile_id, created_at)
            VALUES (:profileId, :blockedProfileId, :now)
            ON CONFLICT (profile_id, blocked_profile_id) DO NOTHING
            """)
    void addIfAbsent(@Param("profileId") Long profileId, @Param("blockedProfileId") Long blockedProfileId,
                     @Param("now") Instant now);

    @Modifying
    @Query("delete from ProfileBlock b where b.profile.id = :profileId and b.blockedProfile.id = :blockedProfileId")
    void remove(@Param("profileId") Long profileId, @Param("blockedProfileId") Long blockedProfileId);

    boolean existsByProfileIdAndBlockedProfileId(Long profileId, Long blockedProfileId);

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
