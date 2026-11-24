package com.dangdang.domain.auth.service;

import com.dangdang.domain.auth.dto.request.KakaoLinkRequest;
import com.dangdang.domain.auth.exception.AuthErrorCode;
import com.dangdang.domain.auth.infra.KakaoPendingStore;
import com.dangdang.domain.auth.infra.kakao.KakaoCodeReader;
import com.dangdang.domain.auth.infra.kakao.KakaoUser;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

// 로그인한 계정에 카카오 붙이기 (L-10 · M-02). 다음부터 카카오로 눌러도 이 계정으로 들어온다 → 기록이 하나로
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoLinkService {

    private final KakaoCodeReader kakaoCodeReader;
    private final KakaoPendingStore pendingStore;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public void link(Long userId, KakaoLinkRequest req) {
        // 카카오 호출(외부)은 줄을 잠그기 전에 끝낸다 → 카카오가 10초 걸려도 내 줄을 잡고 있지 않는다
        KakaoUser kakao = req.byToken()
                ? pendingStore.take(req.kakaoToken())
                        .orElseThrow(() -> new CustomException(AuthErrorCode.KAKAO_PENDING_EXPIRED))
                : kakaoCodeReader.read(req.code(), req.redirectUri());

        // 내 줄을 잠그고 본다 — 연결하기를 두 번 빠르게 눌러도 한 번만
        User me = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        if (me.hasKakao()) {                          // 이미 연결 (카카오로 가입한 계정 포함, M-02b)
            throw new CustomException(AuthErrorCode.KAKAO_ALREADY_CONNECTED);
        }
        if (userRepository.findByProviderSubject(kakao.kakaoId()).isPresent()) {   // 이 카카오로 가입한 계정이 따로 있음
            throw new CustomException(AuthErrorCode.KAKAO_ALREADY_LINKED);
        }
        me.linkKakao(kakao.kakaoId(), clock.instant());
        try {
            userRepository.flush();                   // 다른 계정이 동시에 같은 카카오를 붙이면 UNIQUE가 막음
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(AuthErrorCode.KAKAO_ALREADY_LINKED);
        }
        log.info("[Auth] 카카오 연결 - 사용자: {}", userId);
    }
}
