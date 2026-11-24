package com.dangdang.domain.auth.infra;

import com.dangdang.domain.auth.infra.kakao.KakaoUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

// 카카오로 확인은 됐지만 아직 계정을 안 만든 사람 (K-07 · K-08). Redis kakao:pending:{토큰}, 10분, 한 번만
// 토큰을 가진 사람 = 방금 카카오 동의를 마친 사람. 가입(signup)이나 연결(link)에 성공할 때 한 번 쓰면 지운다
@Component
@RequiredArgsConstructor
public class KakaoPendingStore {

    public static final Duration TTL = Duration.ofMinutes(10);
    private static final String PREFIX = "kakao:pending:";
    private static final String SEP = "\u001F";          // 닉네임에 안 나오는 구분 글자
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    public String save(KakaoUser kakao) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String value = String.join(SEP, kakao.kakaoId(), nullToEmpty(kakao.email()), nullToEmpty(kakao.nickname()));
        redis.opsForValue().set(PREFIX + token, value, TTL);
        return token;
    }

    // 보기만 한다 (지우지 않음) — 검사에서 막혀도 토큰이 남아 K-08 「이메일로 로그인 → 연결」을 이어서 할 수 있게
    public Optional<KakaoUser> peek(String token) {
        return parse(redis.opsForValue().get(PREFIX + token));
    }

    // 꺼내면서 지운다 (Redis GETDEL) → 두 번 눌러도 한 번만 쓰인다. 검사를 다 통과하고 저장하기 직전에만 부른다
    public Optional<KakaoUser> take(String token) {
        return parse(redis.opsForValue().getAndDelete(PREFIX + token));
    }

    private static Optional<KakaoUser> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String[] p = value.split(SEP, -1);
        return Optional.of(new KakaoUser(p[0], emptyToNull(p[1]), emptyToNull(p[2])));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String emptyToNull(String s) {
        return s.isEmpty() ? null : s;
    }
}
