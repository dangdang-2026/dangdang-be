package com.dangdang.domain.auth.infra;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;


//비밀번호 재설정 링크 (AUTH-06: 30분, 한 번만)
@Component
@RequiredArgsConstructor
public class PasswordResetStore {

    public static final Duration LINK_TTL = Duration.ofMinutes(30);
    public static final int MAX_SENDS_PER_DAY = 5;

    private static final String TOKEN = "pwreset:token";       //해시 -> 회원번호
    private static final String USER= "pwreset:user";
    private static final String SENDS="pwreset:sends";

    private final StringRedisTemplate redis;

    public long increaseSendCount(String email){
        String key = SENDS + email + ":" + LocalDate.now(ZoneId.of("Asia/Seoul"));
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, Duration.ofDays(1));
        }
        return count == null ? 0 : count;
    }

    //새 링크 만들면 예전 링크 파기
    public void save(Long userId, String token) {
        String old = redis.opsForValue().get(USER + userId);
        if (old != null) {
            redis.delete(TOKEN + old);
        }
        String hash = hash(token);
        redis.opsForValue().set(TOKEN + hash, String.valueOf(userId), LINK_TTL);
        redis.opsForValue().set(USER + userId, hash, LINK_TTL);
    }

    // 없으면 null
    public Long findUserId(String token){
        String userId = redis.opsForValue().get(TOKEN + hash(token));
        return userId == null ? null : Long.valueOf(userId);
    }

    // 지웠으면 true. 동시에 두 번 눌러도 한 번만 성공하게
    public boolean consume(String token, Long userId) {
        redis.delete(USER + userId);
        return Boolean.TRUE.equals(redis.delete(TOKEN + hash(token)));
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // 자바에 항상 있어서 실제로는 안 남
        }
    }
}