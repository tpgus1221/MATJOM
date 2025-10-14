package com.matjom.matjom.visit.util;

import java.math.BigDecimal;
/**
 * 위경도(지구 표면) 두 점 사이의 대권거리(great-circle distance)를
 * 하버사인(Haversine) 공식을 이용해 미터 단위로 계산하는 유틸 클래스.
 */
public final class GeoDistanceCalculator {

	// 지구 평균 반지름(미터). 하버사인 공식에서 곱해 실제 거리로 변환할 때 사용.
    private static final double EARTH_RADIUS_METERS = 6371000.0;

	// 유틸 클래스이므로 인스턴스화를 금지(잘못 사용 방지)
	private GeoDistanceCalculator() {
        throw new IllegalStateException("Utility class");
    }

	/**
	 * 두 지점의 위도/경도(BigDecimal)로 거리(미터)를 계산.
	 * @param lat1 첫 번째 지점 위도(도 단위, -90~90)
	 * @param lng1 첫 번째 지점 경도(도 단위, -180~180)
	 * @param lat2 두 번째 지점 위도(도 단위)
	 * @param lng2 두 번째 지점 경도(도 단위)
	 * @return 미터 단위 거리. 입력이 하나라도 null이면 Double.MAX_VALUE(센티넬) 반환.
	 */
    public static double distanceMeters(BigDecimal lat1,
                                        BigDecimal lng1,
                                        BigDecimal lat2,
                                        BigDecimal lng2) {
		// 널 입력 방어: 하나라도 null이면 “계산 불가” 의미로 매우 큰 값 반환(호출부에서 필터링용)
		if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return Double.MAX_VALUE;
        }

		// 위도를 라디안으로 변환(삼각함수는 라디안 입력을 받음)
		double latRad1 = Math.toRadians(lat1.doubleValue());
        double latRad2 = Math.toRadians(lat2.doubleValue());
		// 위도 차이(도 단위)를 라디안 차이로 변환
		double deltaLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
		// 경도 차이(도 단위)를 라디안 차이로 변환
		double deltaLng = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());

		// 하버사인 공식: sin^2(Δlat/2), sin^2(Δlng/2) 계산
		double sinLat = Math.sin(deltaLat / 2);
        double sinLng = Math.sin(deltaLng / 2);

		// 하버사인 공식의 핵심 a 값: a = sin^2(Δlat/2) + cos(lat1) * cos(lat2) * sin^2(Δlng/2)
        double a = sinLat * sinLat + Math.cos(latRad1) * Math.cos(latRad2) * sinLng * sinLng;
		// 중심각 c = 2 * atan2( sqrt(a), sqrt(1 - a) )
		double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
		// 실제 거리 = 지구 반경 * 중심각(라디안)
		return EARTH_RADIUS_METERS * c;
    }
}
