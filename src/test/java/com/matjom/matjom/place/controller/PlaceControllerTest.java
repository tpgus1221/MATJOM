package com.matjom.matjom.place.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.matjom.matjom.common.security.jwt.JwtAuthenticationFilter;
import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.place.dto.PlaceDetailResponseDTO;
import com.matjom.matjom.place.dto.PlaceInfoDTO;
import com.matjom.matjom.place.service.PlaceDetailService;
import com.matjom.matjom.statistics.dto.StatsResponseDTO;
import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PlaceController.class)
@AutoConfigureMockMvc(addFilters = false)
class PlaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlaceDetailService placeDetailService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    // 컨트롤러가 Service 응답을 그대로 전달하고 reviewLimit 파라미터를 위임하는지 확인한다.
    void getPlaceDetailReturnsAggregatedResponse() throws Exception {
        PlaceInfoDTO info = PlaceInfoDTO.builder()
                .placeId(5L)
                .name("맛집")
                .address("서울 강남구")
                .categories(List.of("한식"))
                .workingHours(null)
                .breakTime(null)
                .phoneNumber("02-0000-0000")
                .build();

        StatsResponseDTO stats = StatsResponseDTO.builder()
                .placeName("맛집")
                .totalVisitors(200)
                .totalLikes(150)
                .hourlyArrivals(sampleHourly())
                .build();

        ReviewResponseDTO review = ReviewResponseDTO.builder()
                .reviewerName("고객A")
                .text("정말 맛있어요")
                .createdAt(OffsetDateTime.now())
                .build();

        PlaceDetailResponseDTO response = PlaceDetailResponseDTO.of(info, stats, List.of(review),
                PlaceDetailResponseDTO.errorsOf(null, null));

        when(placeDetailService.getPlaceDetail(5L, 3)).thenReturn(response);

        mockMvc.perform(get("/api/places/{placeId}", 5L)
                        .param("reviewLimit", "3")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.info.name").value("맛집"))
                .andExpect(jsonPath("$.data.stats.placeName").value("맛집"))
                .andExpect(jsonPath("$.data.reviews[0].reviewerName").value("고객A"))
                .andExpect(jsonPath("$.data.errors.stats", nullValue()));

        verify(placeDetailService).getPlaceDetail(eq(5L), eq(3));
    }

    private List<StatsResponseDTO.HourlyAverage> sampleHourly() {
        List<StatsResponseDTO.HourlyAverage> hourly = new LinkedList<>();
        for (int hour = 11; hour <= 20; hour++) {
            hourly.add(new StatsResponseDTO.HourlyAverage(hour, hour - 10));
        }
        return hourly;
    }
}
