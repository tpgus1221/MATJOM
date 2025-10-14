package com.matjom.matjom.common.idempotency;

public interface IdempotencyStore {

    <T> IdempotencyResult<T> replayOrRun(String key,
                                         String requestHash,
                                         Class<T> responseType,
                                         IdempotencyCallback<T> callback);
}
