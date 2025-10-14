package com.matjom.matjom.common.ratelimit;

import java.time.Duration;

public interface SearchRateLimiter {

    RateLimitResult consume(String key);

    void rollback(RateLimitResult result);

    long capacity();

    Duration window();
}
