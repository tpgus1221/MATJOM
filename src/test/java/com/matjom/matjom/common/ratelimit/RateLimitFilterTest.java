package com.matjom.matjom.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitFilterTest {

    @Mock
    private SearchRateLimiter rateLimiter;

    private RateLimitFilter filter;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        when(rateLimiter.capacity()).thenReturn(10L);
        when(rateLimiter.window()).thenReturn(Duration.ofSeconds(10));
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        filter = new RateLimitFilter(rateLimiter, mapper);
    }

    @Test
    void allowsRequestAndSetsHeadersWhenWithinLimit() throws ServletException, IOException {
        MockHttpServletRequest request = buildRequest("/api/v1/places", null, "10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiter.consume("rl:places:ip:10.0.0.1"))
                .thenReturn(RateLimitResult.allowed("rl:places:ip:10.0.0.1", 8));

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("10");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("8");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void blocksWhenRateLimitExceeded() throws ServletException, IOException {
        MockHttpServletRequest request = buildRequest("/api/v1/places", null, "10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiter.consume("rl:places:ip:10.0.0.1"))
                .thenReturn(RateLimitResult.blocked("rl:places:ip:10.0.0.1", 5));

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("5");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");

        assertThat(mapper.readTree(response.getContentAsString(StandardCharsets.UTF_8)).get("success").asBoolean()).isFalse();
    }

    @Test
    void consumesUserAndIpKeys() throws ServletException, IOException {
        MockHttpServletRequest request = buildRequest("/api/v1/places", "user-1", "10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiter.consume("rl:places:user:user-1"))
                .thenReturn(RateLimitResult.allowed("rl:places:user:user-1", 9));
        when(rateLimiter.consume("rl:places:ip:10.0.0.1"))
                .thenReturn(RateLimitResult.allowed("rl:places:ip:10.0.0.1", 8));

        filter.doFilter(request, response, new MockFilterChain());

        verify(rateLimiter, times(1)).consume("rl:places:user:user-1");
        verify(rateLimiter, times(1)).consume("rl:places:ip:10.0.0.1");
    }

    @Test
    void skipsFilteringForOtherPaths() throws ServletException, IOException {
        MockHttpServletRequest request = buildRequest("/health", null, "10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        verify(rateLimiter, times(0)).consume(anyString());
    }

    private MockHttpServletRequest buildRequest(String uri, String userId, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (userId != null) {
            request.addHeader("X-User-Id", userId);
        }
        request.setRemoteAddr(ip);
        return request;
    }
}
