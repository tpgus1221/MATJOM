package com.matjom.matjom.place.service;

import com.matjom.matjom.common.exception.base.PlaceException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.place.dto.PlaceDetailResponseDTO;
import com.matjom.matjom.place.dto.PlaceInfoDTO;
import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.place.repository.PlaceReadRepository;
import com.matjom.matjom.place.entity.Place;
import com.matjom.matjom.statistics.dto.StatsResponseDTO;
import com.matjom.matjom.statistics.service.StatisticsService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.matjom.matjom.feed.service.ReviewService;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PlaceDetailService {

    private static final int DEFAULT_REVIEW_LIMIT = 15;

    private final PlaceReadRepository placeReadRepository;
    private final StatisticsService statisticsService;
    private final ReviewService reviewService;

    public PlaceDetailResponseDTO getPlaceDetail(Long placeId) {
        return getPlaceDetail(placeId, null);
    }

    public PlaceDetailResponseDTO getPlaceDetail(Long placeId, Integer reviewLimit) {
        Place place = placeReadRepository.findById(placeId)
                .orElseThrow(() -> new PlaceException(ErrorCode.PLACE_NOT_FOUND));

        PlaceInfoDTO info = PlaceInfoDTO.from(place, buildAddress(place));

        StatsResponseDTO stats = null;
        PlaceDetailResponseDTO.ErrorDetail statsError = null;
        try {
            stats = statisticsService.fetchStats(placeId);
        } catch (Exception ex) {
            statsError = PlaceDetailResponseDTO.ErrorDetail.of("STATS_UNAVAILABLE", "통계 정보를 불러오지 못했습니다");
            log.warn("Failed to load place statistics: placeId={}", placeId, ex);
        }

        List<ReviewResponseDTO> reviews = List.of();
        PlaceDetailResponseDTO.ErrorDetail reviewError = null;
        try {
            reviews = loadReviews(placeId, reviewLimit);
        } catch (Exception ex) {
            reviewError = PlaceDetailResponseDTO.ErrorDetail.of("REVIEWS_UNAVAILABLE", "리뷰 정보를 불러오지 못했습니다");
            log.warn("Failed to load place reviews: placeId={}", placeId, ex);
        }

        Map<String, PlaceDetailResponseDTO.ErrorDetail> errors = PlaceDetailResponseDTO.errorsOf(statsError, reviewError);
        return PlaceDetailResponseDTO.of(info, stats, reviews, errors);
    }

    private String buildAddress(Place place) {
        StringBuilder builder = new StringBuilder();
        append(builder, place.getAddrSido());
        append(builder, place.getAddrSigungu());
        append(builder, place.getAddrEupmyeondong());
        append(builder, place.getAddrStreet());
        append(builder, place.getAddrDetail());
        return builder.toString().trim();
    }

    private void append(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(' ');
        }
        builder.append(value);
    }

    private List<ReviewResponseDTO> loadReviews(Long placeId, Integer reviewLimit) {
        int effectiveLimit = resolveLimit(reviewLimit);
        if (effectiveLimit < 0) {
            return reviewService.getPlaceReviews(placeId);
        }
        return reviewService.getLatestPlaceReviews(placeId, effectiveLimit);
    }

    private int resolveLimit(Integer reviewLimit) {
        if (reviewLimit == null) {
            return DEFAULT_REVIEW_LIMIT;
        }
        if (reviewLimit <= 0) {
            return -1;
        }
        return reviewLimit;
    }
}
