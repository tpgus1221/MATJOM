package com.matjom.matjom.common.exception.message;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // ====== 공통 ======
    INVALID_REQUEST_PARAM(HttpStatus.BAD_REQUEST, "잘못된 요청 파라미터입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근이 거부되었습니다."),

    // ====== Auth / User ======
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    LOGIN_TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "5회 연속 로그인에 실패했습니다. 잠시 후 다시 시도해주세요."),
    USER_INACTIVE(HttpStatus.BAD_REQUEST, "비활성화된 계정입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 만료되었습니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "리프레시 토큰을 찾을 수 없습니다."),
    PASSWORD_MISMATCH(HttpStatus.UNAUTHORIZED, "현재 비밀번호가 일치하지 않습니다."),
    OAUTH_PASSWORD_CHANGE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "소셜 로그인 계정은 비밀번호를 변경할 수 없습니다."),
    WITHDRAW_ALREADY_INACTIVE(HttpStatus.BAD_REQUEST, "이미 탈퇴한 계정입니다."),

    // ====== Session / Geo ======
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "세션을 찾을 수 없습니다."),
    SESSION_ALREADY_EXISTS(HttpStatus.CONFLICT, "세션이 이미 존재합니다."),
    SESSION_EXPIRED(HttpStatus.BAD_REQUEST, "세션이 만료되었습니다."),
    SESSION_ALREADY_INACTIVE(HttpStatus.BAD_REQUEST, "세션이 이미 비활성화되었습니다."),
    GEO_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "위치 권한이 허용되지 않았습니다."),
    GPS_SIGNAL_LOST(HttpStatus.BAD_REQUEST, "GPS 신호를 잃었습니다."),
    INVALID_GEOFENCE(HttpStatus.BAD_REQUEST, "잘못된 지오펜스 요청입니다."),
    ARRIVAL_NOT_CONFIRMED(HttpStatus.BAD_REQUEST, "도착이 확인되지 않았습니다."),
    ARRIVAL_MISDETECTED(HttpStatus.CONFLICT, "잘못된 도착 감지가 발생했습니다."),
    ARRIVAL_CANCELLED(HttpStatus.OK, "도착이 취소되었습니다."),
    NETWORK_UNSTABLE(HttpStatus.BAD_GATEWAY, "네트워크가 불안정합니다."),
    SESSION_RECOVERY_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "세션 복구에 실패했습니다."),

    // ====== Place / Search ======
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소 정보를 찾을 수 없습니다."),
    INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "잘못된 카테고리 요청입니다."),
    SEARCH_EMPTY_RESULT(HttpStatus.OK, "검색 결과가 없습니다."),
    SEARCH_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "요청 한도를 초과했습니다."),
    SEARCH_RELAX_NOT_CONSENTED(HttpStatus.BAD_REQUEST, "완화 검색 동의가 필요합니다."),
    ROULETTE_NO_CANDIDATE(HttpStatus.NO_CONTENT, "추천할 후보가 없습니다."),

    // ====== Feed ======
    REVIEW_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "리뷰 작성이 허용되지 않습니다."),
    REVIEW_NOT_FOUND(HttpStatus.BAD_REQUEST, "리뷰를 찾을 수 없습니다"),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 리뷰를 작성했습니다."),
    REVIEW_BAD_LANGUAGE(HttpStatus.BAD_REQUEST, "부적절한 표현이 감지되었습니다."),
    LIKE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "좋아요를 누를 수 없습니다."),
    LIKE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 좋아요를 눌렀습니다."),
    OPPORTUNITY_EXHAUSTED(HttpStatus.BAD_REQUEST, "기회가 모두 소진되었습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
