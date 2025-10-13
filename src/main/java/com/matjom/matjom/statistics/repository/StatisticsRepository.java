package com.matjom.matjom.statistics.repository;

import com.matjom.matjom.statistics.dto.StatsSnapshot;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class StatisticsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private static final int AVERAGE_WINDOW_DAYS = 14;        // 9월 30일 최종: 최근 14일 평균 계산 기간
    private static final int AVERAGE_WINDOW_OFFSET = AVERAGE_WINDOW_DAYS - 1;

    private static final String SNAPSHOT_SQL = ("""
        SELECT
            (SELECT COALESCE(COUNT(*), 0)
             FROM visits v_total
             WHERE v_total.place_id = :placeId
               AND v_total.arrived_at IS NOT NULL) AS total_visitors,
            (SELECT COALESCE(COUNT(*), 0)
             FROM daily_likes dl
             WHERE dl.place_id = :placeId
               AND dl.status = 'ACTIVE') AS total_likes
        """
    ); // 9월 30일 최종: 누적 방문/좋아요 집계 전용

    private static final String HOURLY_AVERAGE_SQL = ("""
        SELECT hour_key AS hour,
               CAST(COALESCE(ROUND(COUNT(v_hour.id)::numeric / %1$d, 0), 0) AS bigint) AS average_arrivals
        FROM generate_series(11, 20) AS hour_key
        LEFT JOIN visits v_hour
          ON v_hour.place_id = :placeId
         AND v_hour.arrived_at IS NOT NULL
         AND DATE(v_hour.arrived_at AT TIME ZONE 'Asia/Seoul') BETWEEN (:targetDate - INTERVAL '%2$d day') AND :targetDate
         AND EXTRACT(HOUR FROM (v_hour.arrived_at AT TIME ZONE 'Asia/Seoul')) = hour_key
        GROUP BY hour_key
        ORDER BY hour_key
        """
    ).formatted(AVERAGE_WINDOW_DAYS, AVERAGE_WINDOW_OFFSET); // 9월 30일 개편: 11~20시 평균 도착 인원 일평균

    // `/stats` 엔드포인트에 제공할 집계 스냅샷 쿼리를 실행한다.
    public StatsSnapshot fetchSnapshot(Long placeId, LocalDate targetDate) {
        Object[] row = (Object[]) entityManager.createNativeQuery(SNAPSHOT_SQL)
                .setParameter("placeId", placeId)
                .getSingleResult();

        long totalVisitors = ((Number) row[0]).longValue();
        long totalLikes = ((Number) row[1]).longValue();
        Map<Integer, Long> hourlyAverages = fetchHourlyAverages(placeId, targetDate);

        return new StatsSnapshot(totalVisitors, totalLikes, hourlyAverages);
    }

    private Map<Integer, Long> fetchHourlyAverages(Long placeId, LocalDate targetDate) {
        List<Object[]> rows = entityManager.createNativeQuery(HOURLY_AVERAGE_SQL)
                .setParameter("placeId", placeId)
                .setParameter("targetDate", targetDate)
                .getResultList();

        Map<Integer, Long> hourly = new LinkedHashMap<>();
        for (int hour = 11; hour <= 20; hour++) {
            hourly.put(hour, 0L);
        }

        for (Object[] row : rows) {
            if (row == null || row.length < 2) {
                continue;
            }
            Integer hour = ((Number) row[0]).intValue();
            Number average = (Number) row[1];
            hourly.put(hour, average == null ? 0L : average.longValue());
        }

        return hourly;
    }
}
