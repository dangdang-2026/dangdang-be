package com.dangdang.domain.auth.service;

import com.dangdang.domain.auth.dto.response.TokenResponse;
import com.dangdang.domain.auth.exception.AuthErrorCode;
import com.dangdang.domain.auth.infra.kakao.KakaoClient;
import com.dangdang.domain.auth.infra.kakao.KakaoUser;
import com.dangdang.domain.user.entity.NotificationPreference;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.enums.AuthProvider;
import com.dangdang.domain.user.repository.NotificationPreferenceRepository;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.config.property.KakaoProperties;
import com.dangdang.global.exception.CustomException;
import com.dangdang.global.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoLoginService {

    private static final String DEFAULT_NAME = "당당이";   //닉네임 동의를 안 했을 때

    private final KakaoClient kakaoClient;
    private final KakaoProperties kakaoProperties;
    private final UserRepository userRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final TokenService tokenService;

    public record Result(TokenResponse tokens, boolean newUser) {}

    @Transactional
    public Result login(String code, String redirectUri){
        //1) 우리가 콘솔에 등록한 주소만 받기
        if(!kakaoProperties.isAllowedRedirect(redirectUri)){
            throw new CustomException(AuthErrorCode.KAKAO_REDIRECT_NOT_ALLOWED);
        }
        //2) 카카오에 물어보기(코드-> 토큰-> 사용자)
        KakaoUser kakao = kakaoClient.fetchUser(code, redirectUri);

        //3) 이미 카카오로 가입한 사람이면 바로 로그인
        User user = userRepository.findByAuthProviderAndProviderSubject(AuthProvider.KAKAO, kakao.kakaoId()).orElse(null);
        boolean newUser = false;
        if (user == null) {
            user = signup(kakao);
            newUser = true;
        }
        log.info("[Auth] 카카오 로그인 - 사용자: {}, 새 가입: {}", user.getId(), newUser);
        return new Result(tokenService.issue(AuthPrincipal.from(user)), newUser);
    }

    //4) 처음이면 가입, 같은 이메일 계정이 있다면? 합치지 않고 안내하기 (AUTH-05, K-07)
    private User signup(KakaoUser kakao){
        if(kakao.email() != null && userRepository.existsByEmail(User.normalize(kakao.email()))){
            throw new CustomException(AuthErrorCode.EMAIL_ACCOUNT_EXISTS);
        }
        User user = User.ofKakao(kakao.kakaoId(), displayName(kakao.nickname()), kakao.email());
        try{
            userRepository.saveAndFlush(user);      //같은 카카오톡 계정으로 동시에 두 번 오면 DB UNIQUE로 막기
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(AuthErrorCode.KAKAO_LOGIN_FAILED);
        }
        notificationPreferenceRepository.save(NotificationPreference.defaultsFor(user));
        return user;
    }

    //display_name 칸이 10글자 제한.
    private String displayName(String nickname){
        if(nickname == null || nickname.isBlank()) {
            return DEFAULT_NAME;
        }
        String trimmed = nickname.trim();
        return trimmed.length() > 10 ? trimmed.substring(0,10) : trimmed;
    }
}
