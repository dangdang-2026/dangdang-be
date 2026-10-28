package com.dangdang.domain.auth.infra;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

// 살아 있는 Refresh 목록. refresh:{jti} → 회원 번호. 없으면 = 쓴 것·로그아웃한 것
//refresh:user: {회원 번호} -> 그 사람 묶음 (비밀번호 재설정 때 한꺼번에 지우려고)

@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String REFRESH = "refresh:";
    private static final String USER = "refresh:user:";

    private final StringRedisTemplate redis;

    public void save(String jti, Long userId, Duration ttl) {
        redis.opsForValue().set(REFRESH + jti, String.valueOf(userId), ttl);
        redis.opsForSet().add(USER+userId, jti);
        redis.expire(USER+userId, ttl);     //마지막 로그인 기준으로 같이 사라짐

    }

    // 지웠으면 true (= 살아 있던 토큰). 한 번 쓰면 끝나게 하려고 확인 + 삭제를 한 번에
    public boolean delete(String jti) {
        return Boolean.TRUE.equals(redis.delete(REFRESH + jti));
    }

    public void deleteAll(Long userId){
        Set<String> jtis = redis.opsForSet().members(USER+userId);
        if(jtis != null){
            jtis.forEach(jti -> redis.delete(REFRESH + jti));
        }
        redis.delete(USER+userId);
    }
}
