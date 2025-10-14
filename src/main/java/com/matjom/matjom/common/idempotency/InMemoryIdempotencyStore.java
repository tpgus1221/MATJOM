package com.matjom.matjom.common.idempotency;

import com.matjom.matjom.common.exception.base.IdempotencyException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(StringRedisTemplate.class)
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(60);

    private final Map<String, StoredValue> store = new HashMap<>();

    @Override
    public synchronized <T> IdempotencyResult<T> replayOrRun(String key,
                                                             String requestHash,
                                                             Class<T> responseType,
                                                             IdempotencyCallback<T> callback) {
        cleanupExpired(key);
        StoredValue stored = store.get(key);
        if (stored != null) {
            validateHash(stored.requestHash, requestHash);
            Object payload = stored.payload;
            return new IdempotencyResult<>(responseType.cast(payload), true);
        }

        T fresh = callback.execute();
        store.put(key, new StoredValue(requestHash, fresh, System.currentTimeMillis() + DEFAULT_TTL.toMillis()));
        return new IdempotencyResult<>(fresh, false);
    }

    private void cleanupExpired(String key) {
        StoredValue stored = store.get(key);
        if (stored == null) {
            return;
        }
        if (stored.expiresAt <= System.currentTimeMillis()) {
            store.remove(key);
        }
    }

    private void validateHash(String storedHash, String requestHash) {
        if (!Objects.equals(storedHash, requestHash)) {
            throw new IdempotencyException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
    }

    private static final class StoredValue {
        private final String requestHash;
        private final Object payload;
        private final long expiresAt;

        private StoredValue(String requestHash, Object payload, long expiresAt) {
            this.requestHash = requestHash;
            this.payload = payload;
            this.expiresAt = expiresAt;
        }
    }
}
