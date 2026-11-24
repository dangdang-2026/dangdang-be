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
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate tx;

    // 트랜잭션은 아래 tx.executeWithoutResult 안에서만 — 카카오 호출(최대 십몇 초)은 그 전에 끝내서 DB 연결·내 줄을 잡지 않는다
    public void link(Long userId, KakaoLinkRequest req) {
        // 토큰은 보기만 하고(peek) 검사한다 — 막혀도 토큰이 남는다. 쓰는 건(take) 검사를 다 통과한 뒤
        KakaoUser kakao = req.byToken()
                ? pendingStore.peek(req.kakaoToken())
                        .orElseThrow(() -> new CustomException(AuthErrorCode.KAKAO_PENDING_EXPIRED))
                : kakaoCodeReader.read(req.code(), req.redirectUri());

        tx.executeWithoutResult(status -> {
            // 내 줄을 잠그고 본다 — 연결하기를 두 번 빠르게 눌러도 한 번만
            User me = userRepository.findByIdForUpdate(userId)
                    .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
            if (me.hasKakao()) {                          // 이미 연결 (카카오로 가입한 계정 포함, M-02b)
                throw new CustomException(AuthErrorCode.KAKAO_ALREADY_CONNECTED);
            }
            if (userRepository.findByProviderSubject(kakao.kakaoId()).isPresent()) {   // 이 카카오로 가입한 계정이 따로 있음
                throw new CustomException(AuthErrorCode.KAKAO_ALREADY_LINKED);
            }
            if (req.byToken() && pendingStore.take(req.kakaoToken()).isEmpty()) {   // 그 사이 가입·연결에 쓰였으면
                throw new CustomException(AuthErrorCode.KAKAO_PENDING_EXPIRED);
            }
            me.linkKakao(kakao.kakaoId(), clock.instant());
            try {
                userRepository.flush();                   // 다른 계정이 동시에 같은 카카오를 붙이면 UNIQUE가 막음
            } catch (DataIntegrityViolationException e) {
                throw new CustomException(AuthErrorCode.KAKAO_ALREADY_LINKED);
            }
        });
        log.info("[Auth] 카카오 연결 - 사용자: {}", userId);
    }
}
