package com.matjom.matjom.statistics.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public record StatsSnapshot(
        long totalVisitors,                  // 9월 28일 간소화: 누적 방문자 수(ARRIVED 기록)
        long totalLikes,                     // 9월 28일 간소화: 누적 좋아요 수
        Map<Integer, Long> hourlyArrivals    // 9월 30일 개편: 11~20시 시간대별 최근 14일 평균 도착 인원
) {
    public static StatsSnapshot empty() {
        return new StatsSnapshot(
                0L,
                0L,
                defaultHourlyMap());
    }

    private static Map<Integer, Long> defaultHourlyMap() {
        Map<Integer, Long> defaults = new LinkedHashMap<>();
        for (int hour = 11; hour <= 20; hour++) {
            defaults.put(hour, 0L);
        }
        return defaults;
    }
}
