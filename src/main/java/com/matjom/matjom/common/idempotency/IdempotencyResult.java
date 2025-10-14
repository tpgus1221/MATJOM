package com.matjom.matjom.common.idempotency;

import lombok.Getter;

@Getter
public final class IdempotencyResult<T> {

    private final T value;
    private final boolean replayed;

    public IdempotencyResult(T value, boolean replayed) {
        this.value = value;
        this.replayed = replayed;
    }
}
