package com.matjom.matjom.statistics.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = StatsResponseDTO.StatsResponseDTOBuilder.class)
public class StatsResponseDTO {

    private final String placeName;             // 9월 29일 최종: 사용자 가독성을 위한 장소 이름
    private final long totalVisitors;           // 9월 28일 간소화: 누적 방문자 수
    private final long totalLikes;              // 9월 28일 간소화: 누적 좋아요 수
    private final List<HourlyAverage> hourlyArrivals; // 9월 30일 개편: 11~20시 시간대별 평균 도착 인원

    public static StatsResponseDTO of(
            String placeName,
            StatsSnapshot snapshot
    ) {
        Map<Integer, Long> source = snapshot.hourlyArrivals();
        List<HourlyAverage> hourly = new ArrayList<>();
        for (int hour = 11; hour <= 20; hour++) {
            long count = 0L;
            if (source != null && source.containsKey(hour)) {
                count = source.getOrDefault(hour, 0L);
            }
            hourly.add(new HourlyAverage(hour, count));
        }

        return StatsResponseDTO.builder()
                .placeName(placeName)
                .totalVisitors(snapshot.totalVisitors())
                .totalLikes(snapshot.totalLikes())
                .hourlyArrivals(hourly)
                .build();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class StatsResponseDTOBuilder {
        // Lombok will fill
    }

    @Getter
    @AllArgsConstructor
    public static class HourlyAverage {
        private final int hour;          // 9월 30일 개편: 시작 시각(24시간 기준)
        private final long averageCount; // 9월 30일 개편: 최근 14일 평균 도착 인원
    }
}
