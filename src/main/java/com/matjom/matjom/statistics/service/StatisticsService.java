package com.matjom.matjom.statistics.service;

import com.matjom.matjom.common.exception.base.PlaceException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.place.repository.PlaceReadRepository;
import com.matjom.matjom.statistics.dto.StatsResponseDTO;
import com.matjom.matjom.statistics.dto.StatsSnapshot;
import com.matjom.matjom.statistics.repository.StatisticsRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatisticsService {

    private static final ZoneId STATISTICS_ZONE_ID = ZoneId.of("Asia/Seoul"); // 9월 26일 최종: 일자 계산 기준 KST

    private final PlaceReadRepository placeReadRepository;
    private final StatisticsRepository statisticsRepository;
    private final Clock clock;

    public StatisticsService(PlaceReadRepository placeReadRepository,
                             StatisticsRepository statisticsRepository,
                             Clock clock) {
        this.placeReadRepository = placeReadRepository;
        this.statisticsRepository = statisticsRepository;
        this.clock = clock;
    }

    // 장소 존재 여부를 검증하고 최신 통계 스냅샷을 조회한 뒤 DTO로 만들어 반환한다.
    @Transactional(readOnly = true)
    public StatsResponseDTO fetchStats(Long placeId) {
        String placeName = placeReadRepository.findNameById(placeId)
                .orElseThrow(() -> new PlaceException(ErrorCode.PLACE_NOT_FOUND)); // 9월 29일 최종: 이름 확보와 존재 검증 동시 처리

        OffsetDateTime now = OffsetDateTime.now(clock); // 9월 26일 최종: 공통 Clock 주입
        StatsSnapshot snapshot = statisticsRepository.fetchSnapshot(
                placeId,
                now.atZoneSameInstant(STATISTICS_ZONE_ID).toLocalDate()
        );

        return StatsResponseDTO.of(placeName, snapshot); // 9월 30일 개편: 캐시 없이 DB 스냅샷만 반환
    }
}
