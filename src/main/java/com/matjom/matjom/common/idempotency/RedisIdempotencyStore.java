package com.matjom.matjom.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjom.matjom.common.exception.base.IdempotencyException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import java.time.Duration;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConditionalOnBean(org.springframework.data.redis.core.StringRedisTemplate.class)
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    @Autowired
    public RedisIdempotencyStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this(redisTemplate, objectMapper, DEFAULT_TTL);
    }

    public RedisIdempotencyStore(StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper,
                                 Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl == null ? DEFAULT_TTL : ttl;
    }

    @Override
    public <T> IdempotencyResult<T> replayOrRun(String key,
                                                String requestHash,
                                                Class<T> responseType,
                                                IdempotencyCallback<T> callback) {
        ValueOperations<String, String> operations = redisTemplate.opsForValue();
        String existingJson = operations.get(key);
        if (existingJson != null) {
            StoredValue stored = readStored(existingJson);
            validateHash(stored.getRequestHash(), requestHash);
            T response = readPayload(stored.getPayloadJson(), responseType);
            return new IdempotencyResult<>(response, true);
        }

        T freshResponse = callback.execute();
        StoredValue newValue = new StoredValue(requestHash, writePayload(freshResponse));
        String serialized = writeStored(newValue);

        Boolean stored = operations.setIfAbsent(key, serialized, ttl);
        if (Boolean.FALSE.equals(stored)) {
            String latestJson = operations.get(key);
            if (latestJson != null) {
                StoredValue storedValue = readStored(latestJson);
                validateHash(storedValue.getRequestHash(), requestHash);
                T response = readPayload(storedValue.getPayloadJson(), responseType);
                return new IdempotencyResult<>(response, true);
            }
            operations.set(key, serialized, ttl);
            return new IdempotencyResult<>(freshResponse, false);
        }
        if (stored == null || !stored.booleanValue()) {
            operations.set(key, serialized, ttl);
        }
        return new IdempotencyResult<>(freshResponse, false);
    }

    private void validateHash(String storedHash, String requestHash) {
        if (!Objects.equals(storedHash, requestHash)) {
            throw new IdempotencyException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
    }

    private StoredValue readStored(String json) {
        try {
            return objectMapper.readValue(json, StoredValue.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Idempotency 데이터 역직렬화에 실패했습니다.", ex);
        }
    }

    private String writeStored(StoredValue storedValue) {
        try {
            return objectMapper.writeValueAsString(storedValue);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Idempotency 데이터를 직렬화할 수 없습니다.", ex);
        }
    }

    private <T> T readPayload(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Idempotency 페이로드 역직렬화에 실패했습니다.", ex);
        }
    }

    private String writePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Idempotency 페이로드 직렬화에 실패했습니다.", ex);
        }
    }

	@Getter
	@Setter
    private static final class StoredValue {

        private String requestHash;
        private String payloadJson;

        private StoredValue(String requestHash, String payloadJson) {
            this.requestHash = requestHash;
            this.payloadJson = payloadJson;
        }
    }
}
