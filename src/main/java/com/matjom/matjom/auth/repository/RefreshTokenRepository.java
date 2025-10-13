package com.matjom.matjom.auth.repository;

import com.matjom.matjom.common.security.jwt.JwtTokenProvider;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenRepository {
    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    public void save(UUID userId, String refreshToken) {
        String key = KEY_PREFIX + userId;
        Duration expire = Duration.ofMillis(jwtTokenProvider.getRefreshTokenValidity());
        redisTemplate.opsForValue().set(key, refreshToken, expire);
    }

    public Optional<String> find(UUID userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + userId));
    }

    public void delete(UUID userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
    }
}
