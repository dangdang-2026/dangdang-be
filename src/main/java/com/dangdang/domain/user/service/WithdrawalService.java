package com.dangdang.domain.user.service;

import com.dangdang.domain.auth.infra.RefreshTokenStore;
import com.dangdang.domain.user.dto.request.WithdrawalRequest;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.enums.AuthProvider;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.Clock;

// 회원 탈퇴 (M-08). 지체 없이 파기 — users 한 줄을 지우면 DB가 기록·알림·대화·설정을 같이 지운다 (모든 표가 ON DELETE CASCADE)
// 커뮤니티 글은 탈퇴할 때 고른다 (10/22 결정): 기본 「모두 삭제」 / 「남기고 작성자를 탈퇴한 회원으로」
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStore refreshTokenStore;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Transactional
    public void withdraw(Long userId, WithdrawalRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        if (user.getAuthProvider() == AuthProvider.EMAIL
                && (req.password() == null || !passwordEncoder.matches(req.password(), user.getPasswordHash()))) {
            throw new CustomException(UserErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        boolean keepPosts = Boolean.TRUE.equals(req.keepPosts());
        Timestamp now = Timestamp.from(clock.instant());   // 시각은 앱 Clock 하나로 (CommentService.delete와 같게)
        if (!keepPosts) {
            // 프로필은 번호만 SQL로 읽는다 — 엔티티로 읽어 두면 회원을 지울 때 Hibernate가
            // 「지운 회원을 아직 가리키는 프로필」이라며 500
            for (Long profileId : jdbc.queryForList(
                    "SELECT id FROM community_profiles WHERE user_id = ?", Long.class, userId)) {
                // 내 글: 지우면 그 글의 댓글·공감·투표도 DB가 같이 지움
                int posts = jdbc.update("DELETE FROM posts WHERE profile_id = ?", profileId);
                // 남의 글에 단 내 댓글: 답글 구조를 지키려고 줄은 두고 내용만 비움 (화면은 「삭제된 댓글」)
                // 탈퇴 전에 직접 지운 댓글도 비운다 — softDelete는 deleted_at만 적고 내용은 남겨 두기 때문 (지운 시각은 그대로)
                int comments = jdbc.update("UPDATE comments SET content = '', deleted_at = COALESCE(deleted_at, ?)"
                        + " WHERE profile_id = ? AND content <> ''", now, profileId);
                log.info("[탈퇴] 글 {}개 삭제 · 댓글 {}개 비움 - 사용자: {}", posts, comments, userId);
            }
        }
        // 남기기면 프로필이 지워질 때 posts·comments.profile_id가 NULL → 「탈퇴한 회원」 (AuthorResponse)
        userRepository.delete(user);
        // 다른 기기도 로그아웃 — 커밋이 끝난 뒤에 지운다 (커밋이 실패해 탈퇴가 취소되면 로그인은 그대로)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                refreshTokenStore.deleteAll(userId);
            }
        });
        log.info("[탈퇴] 완료 - 사용자: {}, 글 남김: {}", userId, keepPosts);
    }
}
