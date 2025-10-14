package com.matjom.matjom.common.idempotency;

public interface IdempotencyCallback<T> {

    T execute();
}
