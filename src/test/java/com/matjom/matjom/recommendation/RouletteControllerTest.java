package com.matjom.matjom.recommendation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.security.SecurityConfig;
import com.matjom.matjom.recommendation.api.RouletteController;
import com.matjom.matjom.recommendation.dto.RouletteRequest;
import com.matjom.matjom.recommendation.dto.RouletteResponse;
import java.util.List;
import com.matjom.matjom.recommendation.service.RouletteService;
import com.matjom.matjom.common.security.jwt.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;

@WebMvcTest(
        value = RouletteController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("removal")
class RouletteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RouletteService rouletteService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void returns400WhenIdempotencyKeyMissing() throws Exception {
        RouletteRequest request = buildRequest();

        mockMvc.perform(post("/api/v1/recommendations/roulette")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.IDEMPOTENCY_KEY_REQUIRED.name()));
    }

    @Test
    void returns400WhenIdempotencyKeyBlank() throws Exception {
        RouletteRequest request = buildRequest();

        mockMvc.perform(post("/api/v1/recommendations/roulette")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "  ")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.IDEMPOTENCY_KEY_REQUIRED.name()));
    }

    @Test
    void returns400WhenIdempotencyKeyTooLong() throws Exception {
        RouletteRequest request = buildRequest();
        String longKey = "a".repeat(201);

        mockMvc.perform(post("/api/v1/recommendations/roulette")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", longKey)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REQUEST_PARAM.name()));
    }

    @Test
    void returnsOkWhenIdempotencyKeyPresent() throws Exception {
        RouletteRequest request = buildRequest();
        when(rouletteService.recommend(any(RouletteRequest.class), eq("abc-123")))
                .thenReturn(new RouletteResponse(1L, "Place", 42.0, List.of("korean"), 37.5665, 126.9780, new RouletteResponse.Meta(10, false)));

        mockMvc.perform(post("/api/v1/recommendations/roulette")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "abc-123")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(rouletteService).recommend(any(RouletteRequest.class), eq("abc-123"));
    }

    @Test
    void trimsIdempotencyKeyBeforePassingToService() throws Exception {
        RouletteRequest request = buildRequest();
        when(rouletteService.recommend(any(RouletteRequest.class), eq("trimmed")))
                .thenReturn(new RouletteResponse(2L, "Trimmed", 10.0, List.of(), 37.5000, 127.0000, new RouletteResponse.Meta(5, false)));

        mockMvc.perform(post("/api/v1/recommendations/roulette")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "  trimmed  ")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(rouletteService).recommend(any(RouletteRequest.class), eq("trimmed"));
    }

    private RouletteRequest buildRequest() {
        RouletteRequest request = new RouletteRequest();
        request.setLat(37.5665);
        request.setLng(126.9780);
        request.setRadius(300.0);
        request.setCategories(List.of("korean"));
        request.setLimit(100);
        return request;
    }
}
