package com.dangdang.domain.post.repository;

import com.dangdang.domain.post.entity.PostDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PostDraftRepository extends JpaRepository<PostDraft, Long> {

    Optional<PostDraft> findByProfileId(Long profileId);

    void deleteByProfileId(Long profileId);
}
