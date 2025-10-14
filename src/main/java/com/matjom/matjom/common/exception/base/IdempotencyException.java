package com.matjom.matjom.common.exception.base;

import com.matjom.matjom.common.exception.message.ErrorCode;

public class IdempotencyException extends DomainException {

    public IdempotencyException(ErrorCode errorCode) {
        super(errorCode);
    }

    public IdempotencyException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
