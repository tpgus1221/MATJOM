package com.matjom.matjom.place.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Optional;
import org.springframework.util.StringUtils;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PlaceSearchRequest {
	// 위도: 검색 기준점. 클라이언트 GPS/지도 SDK로부터 얻은 값을 그대로 사용.
	// - 유효 범위: [-90, 90]. 범위 밖이면 400(Bad Request)로 요청 자체가 거절됨.
	// - 단위: degree. 서버는 PostGIS에서 meter 연산으로 변환해 사용(geography 기반 거리).
    @NotNull(message = "위도(lat)는 필수입니다.")
    @DecimalMin(value = "-90.0", inclusive = true, message = "위도(lat)는 -90 이상이어야 합니다.")
    @DecimalMax(value = "90.0", inclusive = true, message = "위도(lat)는 90 이하이어야 합니다.")
    private Double lat;

	// 경도: 위도와 동일한 기준점의 X좌표.
	// - 유효 범위: [-180, 180]
    @NotNull(message = "경도(lng)는 필수입니다.")
    @DecimalMin(value = "-180.0", inclusive = true, message = "경도(lng)는 -180 이상이어야 합니다.")
    @DecimalMax(value = "180.0", inclusive = true, message = "경도(lng)는 180 이하이어야 합니다.")
    private Double lng;

	// 검색 반경(m). “근처 탐색” 경험을 위한 입력값.
	// - 미입력 시 서버가 기본값 300m 적용(PlaceSearchService.DEFAULT_RADIUS_METERS).
	// - 너무 큰 반경은 DB 부하·UX 저하 유발 → 상한 유효성/서버 가드로 방어(예: 3,000m 등).
	// - 도착 판정(지오펜스 30m/체류 3분)과는 별개. "검색 반경"은 목록/룰렛용 탐색 스코프임.
    @Positive(message = "반경(radius)은 양수여야 합니다.")
    private Double radius;

	// 페이지 크기. 서버는 항상 500개 상한을 강제하여 과도한 조회를 차단.
	// - 미입력: 기본 20(PlaceSearchService.DEFAULT_PAGE_SIZE).
	// - 상한: 500(PlaceSearchService.MAX_RESULTS_PER_SEARCH). 500 요청 시 서버는 501건을 조회해 상한 초과 여부만 감지(응답은 500개로 컷).
	@Positive(message = "size는 1 이상이어야 합니다.")
    @Max(value = 500, message = "size는 최대 500까지 허용됩니다.")
    private Integer size;

	// 커서 토큰. “거리:마지막ID” 형식(예: "123.45678:42")으로 다음 페이지 시작점을 지정.
	// - 형식 검증: 정규식으로 엄격히 검사(PlaceSearchCursor.TOKEN_PATTERN).
	// - 의미: distanceMeters(정렬 키1)와 placeId(동일 거리 타이브레이커)로 **안정 정렬**을 보장.
	@Pattern(regexp = "^[A-Za-z0-9.,:_-]*$", message = "cursor 형식이 올바르지 않습니다.")
    private String cursor;

	// (예약) 필터 문자열. 카테고리/키워드/정렬옵션 등 확장 포맷을 서버가 파싱할 예정.
	// - 현재 Repository에는 TODO 상태로 남아있음. (추후 "가격/카테고리/혼잡도" 등 조합)
    @Size(max = 200, message = "filters는 200자 이하여야 합니다.")
    private String filters;


	// ----- 비즈니스 헬퍼 -----

	// radius 미지정 시 "300m"을 기본으로 채택해 '근처' UX를 유지한다.
	public double radiusOrDefault(double defaultValue) {
        return radius != null ? radius : defaultValue;
    }

	// size 미지정 시 "20" 기본 페이지 크기를 적용한다.
	public int sizeOrDefault(int defaultValue) {
        return size != null ? size : defaultValue;
    }

	// 커서를 파싱해 (거리m, 마지막ID) 구조체로 변환.
	// - 비어있으면 Optional.empty()
	// - 잘못된 형식이면 PlaceSearchCursor.from(...) 내부에서 SearchException 발생 → 400 처리
    public Optional<PlaceSearchCursor> parseCursor() {
        if (!StringUtils.hasText(cursor)) {
            return Optional.empty();
        }
        return Optional.ofNullable(PlaceSearchCursor.from(cursor));
    }
}
