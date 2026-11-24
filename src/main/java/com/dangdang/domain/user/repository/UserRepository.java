package com.dangdang.domain.user.repository;

import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.enums.AuthProvider;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String normalizedEmail);

    boolean existsByEmail(String normalizedEmail);

    Optional<User> findByAuthProviderAndProviderSubject(AuthProvider provider, String subject);

    // 이 사람 줄을 잠그고 읽는다: 같은 사람의 요청이 동시에 오면 앞 요청이 끝날 때까지 기다린다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    // 이 사람 줄을 「읽기 잠금」(FOR SHARE)으로 읽는다: 건강 기록 저장끼리는 서로 안 막고,
    // 건강정보 철회(FOR UPDATE)와는 차례로 — 철회 중이면 끝날 때까지 기다린다 (SensitiveConsentGuard)
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForShare(@Param("id") Long id);
}
