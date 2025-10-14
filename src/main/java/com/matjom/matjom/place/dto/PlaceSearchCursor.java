package com.matjom.matjom.place.dto;

import com.matjom.matjom.common.exception.base.SearchException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * 커서 토큰: "distanceMeters:lastPlaceId"
 * - 예: "123.45678:42"
 * - distanceMeters: 이전 페이지의 마지막 항목까지의 '누적 거리 키'(정렬 키1). 단위 meter.
 * - lastPlaceId   : 동거리 타이브레이커(정렬 키2). ID가 더 큰 항목부터 다음 페이지가 시작됨.
 *
 * 설계 의도:
 * - "거리 ASC → (동률) ID ASC" 정렬을 전제로, 다음 페이지 조건을
 *   (distance > cursorDistance) OR (distance == cursorDistance AND id > cursorId)
 *   로 정의하여 **결과 중복/누락 없이** 이어지게 함.
 * - Token 표기 형식은 Locale.US 고정(소수점은 '.'). 다국어 환경에서도 커서 파싱이 흔들리지 않도록 함.
 * - 정규식으로 형식 검증하여, 잘못된 커서 사용 시 400으로 명확히 피드백.
 */
public record PlaceSearchCursor(double distanceMeters, long lastPlaceId) {
	// "숫자(:소수 가능):정수" 패턴만 허용해 잘못된 커서로 인한 페이지 왜곡을 방지.
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[0-9]+(?:\\.[0-9]+)?:[0-9]+$");
    private static final Locale FORMAT_LOCALE = Locale.US;
    private static final String TOKEN_FORMAT = "%.5f:%d"; // 소수 5자리 고정 → 토큰 길이/정밀도 균형

    public static PlaceSearchCursor from(String rawCursor) {
        if (!StringUtils.hasText(rawCursor)) {
            return null; // 빈 커서는 사용하지 않음
        }
        String token = rawCursor.trim();
        if (!TOKEN_PATTERN.matcher(token).matches()) {
            throw new SearchException(ErrorCode.INVALID_REQUEST_PARAM, "cursor 형식이 올바르지 않습니다.");
        }
        String[] parts = token.split(":", 2);
        try {
            double distance = Double.parseDouble(parts[0]); // meter 단위(geography 거리)
            long lastId = Long.parseLong(parts[1]);
            return new PlaceSearchCursor(distance, lastId);
        } catch (NumberFormatException ex) {
            throw new SearchException(ErrorCode.INVALID_REQUEST_PARAM, "cursor 형식이 올바르지 않습니다.");
        }
    }

	// 응답 nextCursor 생성 시 사용. 소수 5자리로 고정해 토큰 길이를 안정화(UX/캐시키 균일성).
    public static String toToken(double distanceMeters, long lastPlaceId) {
        return String.format(FORMAT_LOCALE, TOKEN_FORMAT, distanceMeters, lastPlaceId);
    }

    public String toToken() {
        return toToken(distanceMeters, lastPlaceId);
    }
}
