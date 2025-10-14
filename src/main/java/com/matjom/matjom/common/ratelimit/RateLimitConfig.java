package com.matjom.matjom.common.ratelimit;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RateLimitConfig {

    @Bean
    public SearchRateLimiter searchRateLimiter(StringRedisTemplate stringRedisTemplate,
                                               @Value("${ratelimit.search.window-seconds:10}") long windowSeconds,
                                               @Value("${ratelimit.search.capacity:10}") long capacity) {
        Duration window = Duration.ofSeconds(windowSeconds);
        return new RedisSearchRateLimiter(stringRedisTemplate, window, capacity);
    }

    @Bean
    public RateLimitFilter rateLimitFilter(SearchRateLimiter searchRateLimiter,
                                           com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new RateLimitFilter(searchRateLimiter, objectMapper);
    }
}
