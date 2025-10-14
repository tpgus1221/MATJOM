package com.matjom.matjom.common.ratelimit;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Redis {@code INCR + EXPIRE} 로 10초 동안 10회 요청 제한을 관리한다.
 * 멀티 노드 환경에서도 Redis가 단일 진실 공급원이 되어 레이스 컨디션 없이 동작한다.
 */
public class RedisSearchRateLimiter implements SearchRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final Duration window;
    private final long capacity;

    public RedisSearchRateLimiter(StringRedisTemplate redisTemplate, Duration window, long capacity) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.window = Objects.requireNonNull(window, "window");
        this.capacity = capacity;
    }

    @Override
    public RateLimitResult consume(String key) {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        Long current = ops.increment(key);
        if (current == null) {
            throw new IllegalStateException("Redis INCR 응답이 null 입니다.");
        }

        if (current == 1L) {
            redisTemplate.expire(key, window);
        }

        if (current <= capacity) {
            long remaining = capacity - current;
            return RateLimitResult.allowed(key, remaining);
        }

        long retryAfter = resolveRetryAfterSeconds(key);
        return RateLimitResult.blocked(key, retryAfter);
    }

    @Override
    public void rollback(RateLimitResult result) {
        if (result == null) {
            return;
        }
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        Long afterDecrement = ops.decrement(result.key());
        if (afterDecrement != null && afterDecrement <= 0) {
            redisTemplate.delete(result.key());
        }
    }

    @Override
    public long capacity() {
        return capacity;
    }

    @Override
    public Duration window() {
        return window;
    }

    private long resolveRetryAfterSeconds(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl == null || ttl < 0) {
            return window.getSeconds();
        }
        if (ttl == 0) {
            return 1;
        }
        return ttl;
    }
}
