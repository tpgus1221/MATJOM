package com.matjom.matjom.place.dto;

import java.util.List;

/**
 * 검색 응답
 * - places: “거리 ASC, (동일 거리) ID ASC”로 정렬된 페이지 데이터
 * - nextCursor: 다음 페이지를 위한 커서 토큰("distance:lastId"). 더 이상 없으면 null.
 * - meta: 결과 상태에 따른 UX 가이드(선택적)
 *
 * 비즈니스 규칙:
 * - 서버는 요청 size에 +1을 더 조회해(nextCursor 판단) 실제 응답은 size로 절단.
 * - 요청 size가 500이면 서버는 501건 조회로 '상한 초과'를 감지하고, 응답은 500으로 컷.
 * - 거리 단위는 meter(PostGIS ST_Distance(..., true)). 클라이언트는 재계산하지 말고 그대로 표시 권장.
 */
public record PlaceSearchResponse(List<PlaceSummary> places,
                                  String nextCursor,
                                  Meta meta) {
	// meta 없이 간단히 반환하고 싶을 때 사용.
    public PlaceSearchResponse(List<PlaceSummary> places, String nextCursor) {
        this(places, nextCursor, null);
    }

	/**
	 * 단일 장소 요약
	 * - placeId: 동거리 타이브레이커로도 사용되는 안정 정렬 키
	 * - distanceMeters: 기준점(lat/lng)으로부터의 구면거리(미터). 서버 계산값.
	 * - latitude/longitude: 클라이언트 지도 마커 렌더링용(정렬키 아님).
	 */
    public record PlaceSummary(Long placeId,
                               String name,
                               double distanceMeters,
                               double latitude,
                               double longitude) {
    }

	/**
	 * UX 가이드 메타
	 * - reason:
	 *   - "too_many_results": 500 상한 초과 감지. → 반경 축소 or 필터 추가 유도.
	 *   - "low_results"     : 결과가 매우 적음(예: <20). → 반경 확장 or 필터 완화 유도.
	 * - suggest: 프런트 표시용 친절 메시지(간단 한국어). 다국어 필요 시 i18n TODO.
	 */
    public record Meta(String reason, String suggest) {
    }
}
