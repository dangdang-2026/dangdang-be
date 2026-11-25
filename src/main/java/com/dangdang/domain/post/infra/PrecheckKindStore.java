package com.dangdang.domain.post.infra;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

// 올리기 전 확인(precheck)에서 걸린 종류를 30분 기억한다. Redis post:precheck:{회원 번호}:{본문 해시} = "DOSE,MIND"
// 글쓰기 때 같은 사람이 같은 본문으로 올리면 이 종류로 운영자 처리함에 넣는다 (앱이 보내는 값에 기대지 않는다)
// 제목은 확인 때 비어 있을 수 있어서 키에 넣지 않는다
// 글 내용은 Redis에 남기지 않고 해시만 쓴다. Redis가 안 되면 기억 못 한 것으로 보고 글쓰기를 막지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public class PrecheckKindStore {

    static final Duration TTL = Duration.ofMinutes(30);
    private static final String PREFIX = "post:precheck:";

    private final StringRedisTemplate redis;

    // 마지막 확인 결과만 남긴다. 다시 확인해서 걸린 게 없으면 예전 기록을 지운다
    public void save(Long userId, String content, Collection<String> kinds) {
        try {
            String key = key(userId, content);
            if (kinds.isEmpty()) {
                redis.delete(key);
                return;
            }
            redis.opsForValue().set(key, String.join(",", kinds), TTL);
        } catch (DataAccessException e) {
            log.warn("[Precheck] 걸린 종류 저장 실패: {}", e.getClass().getSimpleName());
        }
    }

    // 같은 사람이 같은 내용(앞뒤·겹친 공백 무시)으로 확인했을 때 걸린 종류. 없으면 빈 집합
    public Set<String> find(Long userId, String content) {
        try {
            String value = redis.opsForValue().get(key(userId, content));
            return value == null ? Set.of() : Arrays.stream(value.split(",")).collect(Collectors.toUnmodifiableSet());
        } catch (DataAccessException e) {
            log.warn("[Precheck] 걸린 종류 읽기 실패: {}", e.getClass().getSimpleName());
            return Set.of();
        }
    }

    private static String key(Long userId, String content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(normalize(content).getBytes(StandardCharsets.UTF_8));
            return PREFIX + userId + ":" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);       // SHA-256은 모든 JVM에 있다
        }
    }

    private static String normalize(String s) {
        return s == null ? "" : s.strip().replaceAll("\\s+", " ");
    }
}
