package com.matjom.matjom.auth.rate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class LoginRateLimiter {
    // Redis 키 접두사: 이메일별 실패 횟수를 분리 저장하기 위해 사용한다.
    // 5분 윈도우 동안 허용하는 최대 실패 횟수.
    // 실패 기록을 유지할 시간 창(윈도우) 정의.
    private static final String KEY_PREFIX = "auth:login:failures:";
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    public boolean isLimitReached(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        String attempts = redisTemplate.opsForValue().get(buildKey(email));
        if (!StringUtils.hasText(attempts)) {
            return false;
        }
        return Integer.parseInt(attempts) >= MAX_ATTEMPTS;
    }

    public void recordFailure(String email) {
        if (!StringUtils.hasText(email)) {
            return;
        }
        String key = buildKey(email);
        Long current = redisTemplate.opsForValue().increment(key);
        // 첫 실패 시점에 TTL을 설정해 윈도우가 자연스럽게 만료되도록 한다.
        if (current != null && current == 1) {
            redisTemplate.expire(key, WINDOW);
        }
    }

    public long getRemainingSeconds(String email) {
        if (!StringUtils.hasText(email)) {
            return WINDOW.getSeconds();
        }
        String key = buildKey(email);
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        // TTL이 없으면 기본 윈도우 길이를 안내 값으로 반환한다.
        if (ttl == null || ttl < 0) {
            return WINDOW.getSeconds();
        }
        return ttl;
    }

    public void reset(String email) {
        if (!StringUtils.hasText(email)) {
            return;
        }
        redisTemplate.delete(buildKey(email));
    }

    private String buildKey(String email) {
        return KEY_PREFIX + email;
    }
}
