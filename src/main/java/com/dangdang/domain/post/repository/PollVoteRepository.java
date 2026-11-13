package com.dangdang.domain.post.repository;

import com.dangdang.domain.post.entity.PollVote;
import com.dangdang.global.common.IdCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PollVoteRepository extends JpaRepository<PollVote, Long> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO poll_votes (post_id, profile_id, option_index, created_at)
            VALUES (:postId, :profileId, :optionIndex, :now)
            ON CONFLICT (post_id, profile_id) DO NOTHING
            """)
    void addIfAbsent(@Param("postId") Long postId, @Param("profileId") Long profileId,
                     @Param("optionIndex") int optionIndex, @Param("now") Instant now);

    Optional<PollVote> findByPostIdAndProfileId(Long postId, Long profileId);

    @Query("select cast(v.optionIndex as Long) as id, count(v) as cnt from PollVote v where v.post.id = :postId group by v.optionIndex")
    List<IdCount> countByOption(@Param("postId") Long postId);
}
