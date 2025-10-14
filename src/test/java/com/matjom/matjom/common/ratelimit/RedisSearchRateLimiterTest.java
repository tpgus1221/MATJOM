package com.matjom.matjom.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisSearchRateLimiterTest {

    private static final Duration WINDOW = Duration.ofSeconds(10);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisSearchRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimiter = new RedisSearchRateLimiter(redisTemplate, WINDOW, 10);
    }

    @Test
    void allowsWhenUsageBelowCapacity() {
        when(valueOperations.increment("rl"))
                .thenReturn(1L);

        RateLimitResult result = rateLimiter.consume("rl");

        assertThat(result.allowed()).isTrue();
        assertThat(result.remainingTokens()).isEqualTo(9);
        verify(redisTemplate).expire("rl", WINDOW);
        verify(redisTemplate, never()).getExpire(anyString(), any());
    }

    @Test
    void blocksWhenCapacityExceeded() {
        when(valueOperations.increment("rl"))
                .thenReturn(11L);
        when(redisTemplate.getExpire("rl", TimeUnit.SECONDS))
                .thenReturn(4L);

        RateLimitResult result = rateLimiter.consume("rl");

        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfterSeconds()).isEqualTo(4L);
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void rollbackDeletesKeyWhenCountDropsToZero() {
        when(valueOperations.increment("rl"))
                .thenReturn(1L);
        RateLimitResult result = rateLimiter.consume("rl");

        when(valueOperations.decrement("rl")).thenReturn(0L);

        rateLimiter.rollback(result);

        verify(redisTemplate).delete("rl");
    }

    @Test
    void retryAfterFallsBackToWindowWhenTtlMissing() {
        when(valueOperations.increment("rl"))
                .thenReturn(11L);
        when(redisTemplate.getExpire("rl", TimeUnit.SECONDS))
                .thenReturn(-1L);

        RateLimitResult result = rateLimiter.consume("rl");

        assertThat(result.retryAfterSeconds()).isEqualTo(WINDOW.getSeconds());
    }
}
