package com.dangdang.domain.user.repository;

import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String normalizedEmail);

    boolean existsByEmail(String normalizedEmail);

    Optional<User> findByAuthProviderAndProviderSubject(AuthProvider provider, String subject);
}
