package com.matjom.matjom.auth.repository;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TokenBlacklistRepository {

    private static final String KEY_PREFIX = "blacklist:";

    private final StringRedisTemplate redisTemplate;

    public void save(String token, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + token, "1", ttl);
    }

    public boolean exists(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
    }
}
