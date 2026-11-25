package com.dangdang.domain.user.repository;

import com.dangdang.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String normalizedEmail);

    boolean existsByEmail(String normalizedEmail);

    // 카카오 로그인·연결 — 가입 방식과 상관없이 이 카카오 번호를 가진 계정
    Optional<User> findByProviderSubject(String kakaoId);

    // 이 사람 줄을 잠그고 읽는다: 같은 사람의 요청이 동시에 오면 앞 요청이 끝날 때까지 기다린다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    // 이 사람 줄을 「읽기 잠금」(FOR SHARE)으로 읽는다: 건강 기록 저장끼리는 서로 안 막고,
    // 건강정보 철회(FOR UPDATE)와는 차례로 — 철회 중이면 끝날 때까지 기다린다 (SensitiveConsentGuard)
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForShare(@Param("id") Long id);

    // 이 사람 줄에 가장 약한 잠금(FOR KEY SHARE)만 걸기: 같이 읽거나 고치는 건 막지 않고, 탈퇴(줄 지우기)만 기다리게 함
    // 진료 알림이 일정 줄보다 먼저 잡아서 탈퇴와 잠그는 순서(회원 → 일정)를 맞춤 → 서로 막는 데드락이 안 생김
    @Query(value = "SELECT id FROM users WHERE id = :id FOR KEY SHARE", nativeQuery = true)
    Optional<Long> lockForKeyShare(@Param("id") Long id);

    // 이메일 필수 전(11/24)에 카카오로 가입해 이메일이 빈 계정 → 카카오가 인증한 이메일로 채운다 (한 줄 UPDATE)
    // 다른 계정이 그 이메일을 쓰면(대소문자 무시) 안 채운다 (합치지 않는다, AUTH-05). 채웠으면 1, 아니면 0
    // 확인과 쓰기가 한 문장이라 「없는 줄 알았는데 커밋 때 ux_users_email에 걸려 500」이 없다
    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE users SET email = :email, updated_at = :now, row_version = row_version + 1
            WHERE id = :id AND auth_provider = 'KAKAO' AND email IS NULL
              AND NOT EXISTS (SELECT 1 FROM users o WHERE lower(o.email) = lower(:email))
            """)
    int fillKakaoEmailIfFree(@Param("id") Long id, @Param("email") String normalizedEmail, @Param("now") Instant now);
}
