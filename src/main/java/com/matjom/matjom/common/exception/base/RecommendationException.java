package com.matjom.matjom.common.exception.base;

import com.matjom.matjom.common.exception.message.ErrorCode;

public class RecommendationException extends DomainException {

    public RecommendationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public RecommendationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
