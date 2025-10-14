package com.matjom.matjom.common.exception.handler;

import com.matjom.matjom.common.exception.base.DomainException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainException(DomainException ex) {
        ErrorCode code = ex.getErrorCode();
        String message = ex.getMessage() == null ? code.getDefaultMessage() : ex.getMessage();
        if (log.isWarnEnabled()) {
            log.warn("Domain exception occurred: code={}, message={}", code.name(), message);
        }
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code, message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ErrorCode.INVALID_REQUEST_PARAM.getDefaultMessage();
        if (!ex.getBindingResult().getFieldErrors().isEmpty()) {
            String defaultMessage = ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
            if (defaultMessage != null) {
                message = defaultMessage;
            }
        }

        log.debug("Validation failed: {}", message, ex);
        ErrorCode code = ErrorCode.INVALID_REQUEST_PARAM;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnhandledException(Exception ex) {
        log.error("Unhandled exception", ex);
        ErrorCode code = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code, code.getDefaultMessage()));
    }
}
