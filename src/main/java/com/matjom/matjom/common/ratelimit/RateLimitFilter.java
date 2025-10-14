package com.matjom.matjom.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_RATE_LIMIT_LIMIT = "X-RateLimit-Limit";
    private static final String HEADER_RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
    private static final String HEADER_RETRY_AFTER = "Retry-After";

    private final SearchRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(SearchRateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equalsIgnoreCase(request.getMethod())
                || !request.getRequestURI().startsWith("/api/v1/places");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        List<RateLimitResult> consumed = new ArrayList<>(2);
        try {
            List<String> keys = buildKeys(request);
            if (keys.isEmpty()) {
                filterChain.doFilter(request, response);
                return;
            }

            RateLimitResult blockingResult = null;
            long remainingTokens = rateLimiter.capacity();

            for (String key : keys) {
                RateLimitResult result = rateLimiter.consume(key);
                if (!result.allowed()) {
                    blockingResult = result;
                    break;
                }
                consumed.add(result);
                remainingTokens = Math.min(remainingTokens, result.remainingTokens());
            }

            if (blockingResult != null) {
                for (RateLimitResult result : consumed) {
                    rateLimiter.rollback(result);
                }
                writeTooManyResponse(response, blockingResult);
                return;
            }

            writeSuccessHeaders(response, remainingTokens);
            filterChain.doFilter(request, response);
        } finally {
            consumed.clear();
        }
    }

    private List<String> buildKeys(HttpServletRequest request) {
        List<String> keys = new ArrayList<>(2);
        String userId = extractUserId(request);
        if (StringUtils.hasText(userId)) {
            keys.add("rl:places:user:" + userId);
        }
        String clientIp = extractClientIp(request);
        if (StringUtils.hasText(clientIp)) {
            keys.add("rl:places:ip:" + clientIp);
        }
        return keys;
    }

    private void writeSuccessHeaders(HttpServletResponse response, long remainingTokens) {
        response.setHeader(HEADER_RATE_LIMIT_LIMIT, Long.toString(rateLimiter.capacity()));
        response.setHeader(HEADER_RATE_LIMIT_REMAINING, Long.toString(Math.max(remainingTokens, 0)));
    }

    private void writeTooManyResponse(HttpServletResponse response, RateLimitResult blockingResult) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HEADER_RATE_LIMIT_LIMIT, Long.toString(rateLimiter.capacity()));
        response.setHeader(HEADER_RATE_LIMIT_REMAINING, "0");
        response.setHeader(HEADER_RETRY_AFTER, Long.toString(blockingResult.retryAfterSeconds()));

        String message = String.format(Locale.KOREA, "%d초 동안 %d회 요청 한도를 초과했습니다.",
                rateLimiter.window().toSeconds(),
                rateLimiter.capacity());
        ApiResponse<Void> payload = ApiResponse.error(ErrorCode.SEARCH_RATE_LIMIT_EXCEEDED, message);
        response.getWriter().write(objectMapper.writeValueAsString(payload));
    }

    private String extractUserId(HttpServletRequest request) {
        String headerUserId = request.getHeader(HEADER_USER_ID);
        if (StringUtils.hasText(headerUserId)) {
            return headerUserId.trim();
        }
        Object attributeUserId = request.getAttribute("userId");
        if (attributeUserId != null) {
            return attributeUserId.toString();
        }
        return null;
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int commaIndex = forwarded.indexOf(',');
            return commaIndex >= 0 ? forwarded.substring(0, commaIndex).trim() : forwarded.trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr.trim() : null;
    }
}
