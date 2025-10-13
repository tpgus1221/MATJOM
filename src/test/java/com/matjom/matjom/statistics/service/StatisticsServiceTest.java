package com.matjom.matjom.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matjom.matjom.common.exception.base.PlaceException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.place.repository.PlaceReadRepository;
import com.matjom.matjom.statistics.dto.StatsResponseDTO;
import com.matjom.matjom.statistics.dto.StatsSnapshot;
import com.matjom.matjom.statistics.repository.StatisticsRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock
    private PlaceReadRepository placeReadRepository;

    @Mock
    private StatisticsRepository statisticsRepository;

    @Captor
    private ArgumentCaptor<java.time.LocalDate> dateCaptor;


    private StatisticsService statisticsService;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2024-09-26T06:15:00Z"), ZoneOffset.UTC); // 9월 26일 최종: 테스트 고정 시각

    @BeforeEach
    void setUp() {
        statisticsService = new StatisticsService(placeReadRepository, statisticsRepository, fixedClock);
    }

    @Test
    // 장소가 존재할 때 스냅샷이 올바르게 조립되고 시간대 계산이 KST 기준으로 수행되는지 확인한다.
    void fetchStatsReturnsSnapshot() {
        Long placeId = 10L;
        when(placeReadRepository.findNameById(placeId)).thenReturn(Optional.of("테스트 장소"));
        Map<Integer, Long> hourly = new LinkedHashMap<>();
        hourly.put(11, 8L);
        hourly.put(12, 5L);
        hourly.put(13, 3L);
        StatsSnapshot snapshot = new StatsSnapshot(120L, 45L, hourly);
        when(statisticsRepository.fetchSnapshot(eq(placeId), any())).thenReturn(snapshot);

        StatsResponseDTO response = statisticsService.fetchStats(placeId);

        assertThat(response.getPlaceName()).isEqualTo("테스트 장소");
        assertThat(response.getTotalVisitors()).isEqualTo(120L);
        assertThat(response.getTotalLikes()).isEqualTo(45L);
        assertThat(response.getHourlyArrivals()).hasSize(10);
        assertThat(response.getHourlyArrivals().get(0).getHour()).isEqualTo(11);
        assertThat(response.getHourlyArrivals().get(0).getAverageCount()).isEqualTo(8L);
        assertThat(response.getHourlyArrivals().get(1).getHour()).isEqualTo(12);
        assertThat(response.getHourlyArrivals().get(1).getAverageCount()).isEqualTo(5L);
        assertThat(response.getHourlyArrivals().get(2).getHour()).isEqualTo(13);
        assertThat(response.getHourlyArrivals().get(2).getAverageCount()).isEqualTo(3L);

        OffsetDateTime expectedNow = OffsetDateTime.now(fixedClock);

        verify(statisticsRepository).fetchSnapshot(eq(placeId), dateCaptor.capture());
        assertThat(dateCaptor.getValue()).isEqualTo(expectedNow.atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDate());
    }

    @Test
    // 장소가 없을 때 예외가 발생하는지 검증해 방어 로직을 보장한다.
    void fetchStatsThrowsWhenPlaceNotFound() {
        Long placeId = 99L;
        when(placeReadRepository.findNameById(placeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> statisticsService.fetchStats(placeId))
                .isInstanceOf(PlaceException.class)
                .extracting(ex -> ((PlaceException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PLACE_NOT_FOUND);
    }
}
