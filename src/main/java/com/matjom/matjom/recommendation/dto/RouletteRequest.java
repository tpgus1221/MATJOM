package com.matjom.matjom.recommendation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/*
* RouletteRequest는 기본값 헬퍼(radiusOrDefault, limitOrDefault)로 null 처리 부담을 서비스에서 제거하고,
* 카테고리 배열은 없으면 null을 반환해 SQL에서 :categories IS NULL 분기를 타도록 설계되었습니다.
* seed는 QA나 리플레이 상황에서 동일 결과를 만들기 위한 도구이며, 미지정 시 서버가 균등 난수로 선택합니다.
* */
public class RouletteRequest {
    @NotNull(message = "위도(lat)는 필수입니다.")
    @DecimalMin(value = "-90.0", message = "위도(lat)는 -90 이상이어야 합니다.")
    @DecimalMax(value = "90.0", message = "위도(lat)는 90 이하이어야 합니다.")
    private Double lat; // [검색기준점] 위도. 후보 조회의 절대 기준(도착판정과는 별개 정책).

    @NotNull(message = "경도(lng)는 필수입니다.")
    @DecimalMin(value = "-180.0", message = "경도(lng)는 -180 이상이어야 합니다.")
    @DecimalMax(value = "180.0", message = "경도(lng)는 180 이하이어야 합니다.")
    private Double lng; // [검색기준점] 경도. PostGIS geography(m) 거리 계산의 기준점.

    @Positive(message = "반경(radius)은 양수여야 합니다.")
    private Double radius; // [UX 스코프] 검색 반경(m). 미지정시 정책 기본값(예: 300m).

    @Size(max = 5, message = "categories는 최대 5개까지 허용됩니다.")
	// [후보 상한] 후보풀 최대크기(샘플링 전). 과대 후보풀로 인한 비용/지연 방지.
    private List<@Size(min = 1, max = 30, message = "카테고리는 1~30자여야 합니다.") String> categories; // 카테고리 교집합 필터

    @Positive(message = "limit은 1 이상이어야 합니다.")
    private Integer limit; // [선택 필터] 빈/누락이면 전체 허용. 화이트리스트 매핑으로 SQL 주입 방지.

    private Long seed; // [재현성] 동일 seed + 동일 후보풀 ⇒ 항상 동일 후보가 선택됨(테스트/디버그/리플레이 용도).

	// ----- 헬퍼(서비스 단순화) -----
	public double radiusOrDefault(double defaultValue) {
        return radius != null ? radius : defaultValue;
    }

    public int limitOrDefault(int defaultValue) {
        return limit != null ? limit : defaultValue;
    }

    public List<String> categoriesOrNull() {
		// [SQL 분기] null이면 “카테고리 조건 없음” 분기로 흘려보내기 위함
		if (categories == null || categories.isEmpty()) {
            return null;
        }
        return categories;
    }
}
