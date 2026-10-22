package com.dangdang.domain.user.entity;

import com.dangdang.domain.user.enums.AuthProvider;
import com.dangdang.domain.user.enums.Role;
import com.dangdang.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email; // 카카오는 없을 수 있다

    @Column(nullable = false, length = 10)
    private String displayName; // 부를 이름

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuthProvider authProvider;

    private String providerSubject; // 카카오 회원번호

    private String passwordHash; // 이메일 가입만

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    private Instant emailVerifiedAt;

    @Version
    @Column(nullable = false)
    private Long rowVersion;

    // 이메일 가입: 인증을 마친 뒤에만 만든다
    public static User ofEmail(
            String email, String displayName, String passwordHash, Instant verifiedAt) {
        User user = new User();
        user.email = normalize(email);
        user.displayName = displayName;
        user.authProvider = AuthProvider.EMAIL;
        user.passwordHash = passwordHash;
        user.role = Role.USER;
        user.emailVerifiedAt = verifiedAt;
        return user;
    }

    // 카카오 가입: 이메일 없이도 만든다
    public static User ofKakao(String kakaoId, String displayName, String emailOrNull) {
        User user = new User();
        user.email = emailOrNull == null ? null : normalize(emailOrNull);
        user.displayName = displayName;
        user.authProvider = AuthProvider.KAKAO;
        user.providerSubject = kakaoId;
        user.role = Role.USER;
        return user;
    }

    // 앞뒤 공백 제거 + 소문자. 저장할 때도 찾을 때도 항상 거친다
    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
