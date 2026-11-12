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
}
