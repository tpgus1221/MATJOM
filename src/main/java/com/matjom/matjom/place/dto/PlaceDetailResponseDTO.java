package com.matjom.matjom.place.dto;

import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.statistics.dto.StatsResponseDTO;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceDetailResponseDTO {

    private PlaceInfoDTO info;
    private StatsResponseDTO stats;
    private List<ReviewResponseDTO> reviews;
    private Map<String, ErrorDetail> errors;

    public static PlaceDetailResponseDTO of(PlaceInfoDTO info,
                                            StatsResponseDTO stats,
                                            List<ReviewResponseDTO> reviews,
                                            Map<String, ErrorDetail> errors) {
        return PlaceDetailResponseDTO.builder()
                .info(info)
                .stats(stats)
                .reviews(reviews == null ? List.of() : List.copyOf(reviews))
                .errors(normalizeErrors(errors))
                .build();
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ErrorDetail {
        private String code;
        private String message;

        public static ErrorDetail of(String code, String message) {
            return new ErrorDetail(code, message);
        }
    }

    public static Map<String, ErrorDetail> errorsOf(ErrorDetail statsError, ErrorDetail reviewError) {
        Map<String, ErrorDetail> map = new LinkedHashMap<>();
        map.put("stats", statsError);
        map.put("reviews", reviewError);
        return normalizeErrors(map);
    }

    private static Map<String, ErrorDetail> normalizeErrors(Map<String, ErrorDetail> errors) {
        if (errors == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }
}
