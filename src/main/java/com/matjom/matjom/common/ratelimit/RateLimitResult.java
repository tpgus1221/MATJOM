package com.matjom.matjom.common.ratelimit;

/**
 * 결과 상한 500 처리 이후 적용되는 검색 API 전용 레이트리밋 결과 값.
 *
 * @param key                레이트리밋 키(예: rl:places:user:123)
 * @param allowed            토큰 소비 성공 여부
 * @param remainingTokens    남은 토큰 수(최소 0)
 * @param retryAfterSeconds  재시도까지 남은 초(차단된 경우 1 이상)
 */
public record RateLimitResult(String key,
                              boolean allowed,
                              long remainingTokens,
                              long retryAfterSeconds) {

    public static RateLimitResult allowed(String key, long remainingTokens) {
        return new RateLimitResult(key, true, Math.max(remainingTokens, 0), 0);
    }

    public static RateLimitResult blocked(String key, long retryAfterSeconds) {
        long wait = retryAfterSeconds > 0 ? retryAfterSeconds : 1;
        return new RateLimitResult(key, false, 0, wait);
    }
}
