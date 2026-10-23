package com.dangdang.domain.auth.infra;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

// 살아 있는 Refresh 목록. refresh:{jti} → 회원 번호. 없으면 = 쓴 것·로그아웃한 것
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String REFRESH = "refresh:";

    private final StringRedisTemplate redis;

    public void save(String jti, Long userId, Duration ttl) {
        redis.opsForValue().set(REFRESH + jti, String.valueOf(userId), ttl);
    }

    // 지웠으면 true (= 살아 있던 토큰). 한 번 쓰면 끝나게 하려고 "확인 + 삭제"를 한 번에
    public boolean delete(String jti) {
        return Boolean.TRUE.equals(redis.delete(REFRESH + jti));
    }
}
