package com.matjom.matjom.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.matjom.matjom.common.exception.base.PlaceException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.place.dto.PlaceDetailResponseDTO;
import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.feed.service.ReviewService;
import com.matjom.matjom.place.repository.PlaceReadRepository;
import com.matjom.matjom.place.entity.Place;
import com.matjom.matjom.statistics.dto.StatsResponseDTO;
import com.matjom.matjom.statistics.service.StatisticsService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceDetailServiceTest {

    @Mock
    private PlaceReadRepository placeReadRepository;

    @Mock
    private StatisticsService statisticsService;

    @Mock
    private ReviewService reviewService;

    @InjectMocks
    private PlaceDetailService placeDetailService;

    private static final Long PLACE_ID = 10L;

    @Test
    @DisplayName("장소 상세 정보가 정상 반환된다")
    void getPlaceDetailReturnsData() {
        Place place = buildPlace();
        given(placeReadRepository.findById(PLACE_ID)).willReturn(Optional.of(place));
        StatsResponseDTO stats = StatsResponseDTO.builder()
                .placeName("테스트 장소")
                .totalVisitors(100)
                .totalLikes(50)
                .hourlyArrivals(List.of())
                .build();
        given(statisticsService.fetchStats(PLACE_ID)).willReturn(stats);
        ReviewResponseDTO review = ReviewResponseDTO.builder()
                .reviewerName("홍길동")
                .text("훌륭해요")
                .createdAt(OffsetDateTime.now())
                .build();
        given(reviewService.getLatestPlaceReviews(PLACE_ID, 15)).willReturn(List.of(review));

        PlaceDetailResponseDTO response = placeDetailService.getPlaceDetail(PLACE_ID);

        assertThat(response.getInfo().getName()).isEqualTo("테스트 장소");
        assertThat(response.getStats()).isEqualTo(stats);
        assertThat(response.getReviews()).hasSize(1);
        assertThat(response.getErrors()).containsEntry("stats", null).containsEntry("reviews", null);
        verify(statisticsService).fetchStats(PLACE_ID);
        verify(reviewService).getLatestPlaceReviews(PLACE_ID, 15);
    }

    @Test
    @DisplayName("통계 조회 실패 시 errors에 정보가 저장되고 다른 데이터는 유지된다")
    void statsFailureDoesNotBreakResponse() {
        Place place = buildPlace();
        given(placeReadRepository.findById(PLACE_ID)).willReturn(Optional.of(place));
        given(statisticsService.fetchStats(PLACE_ID)).willThrow(new RuntimeException("stats error"));
        given(reviewService.getLatestPlaceReviews(PLACE_ID, 15)).willReturn(List.of());

        PlaceDetailResponseDTO response = placeDetailService.getPlaceDetail(PLACE_ID);

        assertThat(response.getStats()).isNull();
        assertThat(response.getErrors()).containsKey("stats");
        assertThat(response.getErrors().get("stats").getCode()).isEqualTo("STATS_UNAVAILABLE");
        assertThat(response.getErrors().get("reviews")).isNull();
    }

    @Test
    @DisplayName("리뷰 조회 실패 시 errors에 정보가 저장되고 다른 데이터는 유지된다")
    void reviewFailureDoesNotBreakResponse() {
        Place place = buildPlace();
        given(placeReadRepository.findById(PLACE_ID)).willReturn(Optional.of(place));
        given(statisticsService.fetchStats(PLACE_ID)).willReturn(null);
        given(reviewService.getLatestPlaceReviews(PLACE_ID, 15)).willThrow(new RuntimeException("review error"));

        PlaceDetailResponseDTO response = placeDetailService.getPlaceDetail(PLACE_ID);

        assertThat(response.getReviews()).isEmpty();
        assertThat(response.getErrors()).containsKey("reviews");
        assertThat(response.getErrors().get("reviews").getCode()).isEqualTo("REVIEWS_UNAVAILABLE");
        assertThat(response.getErrors().get("stats")).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 장소면 예외 발생")
    void throwsWhenPlaceMissing() {
        given(placeReadRepository.findById(anyLong())).willReturn(Optional.empty());

        PlaceException exception = org.junit.jupiter.api.Assertions.assertThrows(PlaceException.class,
                () -> placeDetailService.getPlaceDetail(PLACE_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLACE_NOT_FOUND);
    }

    @Test
    @DisplayName("reviewLimit가 0 이하이면 모든 리뷰를 조회한다")
    void reviewLimitZeroFetchesAllReviews() {
        Place place = buildPlace();
        given(placeReadRepository.findById(PLACE_ID)).willReturn(Optional.of(place));
        given(statisticsService.fetchStats(PLACE_ID)).willReturn(null);
        given(reviewService.getPlaceReviews(PLACE_ID)).willReturn(List.of());

        PlaceDetailResponseDTO response = placeDetailService.getPlaceDetail(PLACE_ID, 0);

        assertThat(response.getReviews()).isEmpty();
        verify(reviewService).getPlaceReviews(PLACE_ID);
    }

    private Place buildPlace() {
        Place place = org.mockito.Mockito.mock(Place.class);
        given(place.getId()).willReturn(PLACE_ID);
        given(place.getName()).willReturn("테스트 장소");
        given(place.getAddrSido()).willReturn("서울특별시");
        given(place.getAddrSigungu()).willReturn("강남구");
        given(place.getAddrEupmyeondong()).willReturn("역삼동");
        given(place.getAddrStreet()).willReturn("테헤란로 1");
        given(place.getAddrDetail()).willReturn("101호");
        given(place.getCategory()).willReturn(List.of("카페"));
        given(place.getWorkingHours()).willReturn(null);
        given(place.getBreakTime()).willReturn(null);
        given(place.getPhoneNumber()).willReturn("02-000-0000");
        return place;
    }
}
