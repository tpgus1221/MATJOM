package com.matjom.matjom.visit.geofence;

import com.matjom.matjom.place.entity.Place;
import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitPosition;
import com.matjom.matjom.visit.entity.VisitState;
import com.matjom.matjom.visit.util.GeoDistanceCalculator;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class DefaultGeoFenceEvaluator implements GeoFenceEvaluator {

    private static final double MAX_DISTANCE_METERS = 30.0;
    private static final double MAX_ACCURACY_METERS = 30.0;
    private static final long REQUIRED_DWELL_SECONDS = 180L;
    private static final long GRACE_SECONDS = 10L;

    @Override
    public GeoFenceEvaluationResult evaluate(Visit visit, VisitPosition position) {
        Place place = visit.getPlace();
        BigDecimal placeLat = place.getLatitude();
        BigDecimal placeLng = place.getLongitude();

        boolean wasInside = false;
        if (visit.getLastLatitude() != null && visit.getLastLongitude() != null) {
            double previousDistance = GeoDistanceCalculator.distanceMeters(
                    placeLat,
                    placeLng,
                    visit.getLastLatitude(),
                    visit.getLastLongitude());
            wasInside = previousDistance <= MAX_DISTANCE_METERS;
        }

        OffsetDateTime recordedAt = position.getReceivedAt();
        OffsetDateTime lastRecordedAt = visit.getLastPositionAt();

        boolean accuracyPaused = false;
        BigDecimal accuracyValue = position.getAccuracyMeter();
        if (accuracyValue != null && accuracyValue.doubleValue() > MAX_ACCURACY_METERS) {
            accuracyPaused = true;
        }

        boolean insideFence = false;
        if (!accuracyPaused) {
            double distanceMeters = GeoDistanceCalculator.distanceMeters(
                    placeLat,
                    placeLng,
                    position.getLatitude(),
                    position.getLongitude());
            insideFence = distanceMeters <= MAX_DISTANCE_METERS;

            if (insideFence) {
                visit.startDwellIfAbsent(recordedAt);
            } else {
                boolean withinGrace = false;
                if (wasInside && lastRecordedAt != null) {
                    long secondsSinceLast = Duration.between(lastRecordedAt, recordedAt).getSeconds();
                    withinGrace = secondsSinceLast >= 0 && secondsSinceLast <= GRACE_SECONDS;
                }
                if (!withinGrace) {
                    visit.resetDwell();
                }
            }
        }

		// 체류 시간(초 단위)을 계산할 변수, 기본값은 0
        long dwellSeconds = 0L;

		// 방문(Visit) 객체에서 dwell(체류)이 시작된 시각을 가져옴
        OffsetDateTime dwellStarted = visit.getDwellStartedAt();

		// dwell 시작 시간이 기록되어 있다면 → 체류 시간을 계산할 수 있음
		if (dwellStarted != null) {
			// 이번에 새로 받은 위치 기록의 시각
            OffsetDateTime effectiveTimestamp = recordedAt;

			// 정확도가 너무 낮아서(accuracyPaused) 이번 기록을 무시해야 한다면,
			// → 체류 시간을 '이번 시각(recordedAt)' 대신 '직전 시각(lastRecordedAt)' 기준으로 계산
			//   즉, GPS 오차 때문에 dwell이 "진행되지 않은 것처럼" 처리
            if (accuracyPaused && lastRecordedAt != null) {
                effectiveTimestamp = lastRecordedAt;
            }

			// dwell 시작 시각 ~ 유효 시각(effectiveTimestamp) 간의 경과 시간을 초 단위로 구함
			dwellSeconds = Duration.between(dwellStarted, effectiveTimestamp).getSeconds();

			// 혹시나 음수(시계 역전/데이터 오류)가 나오면 안전하게 0으로 보정
			if (dwellSeconds < 0) {
                dwellSeconds = 0L;
            }
        }

		// === 도착(Arrive) 판정 ===
		// 1) accuracyPaused == false → 정확도가 충분히 좋아야 함
		// 2) insideFence == true → 현재 위치가 지오펜스(30m 반경) 안에 있어야 함
		// 3) visit.getDwellStartedAt() != null → dwell이 시작된 상태여야 함
		// 4) dwellSeconds >= REQUIRED_DWELL_SECONDS → 체류 시간이 요구 조건(예: 180초 = 3분) 이상
		// 5) visit.getState() == VisitState.ACTIVE → 방문 상태가 진행 중이어야 함
        if (!accuracyPaused && insideFence && visit.getDwellStartedAt() != null && dwellSeconds >= REQUIRED_DWELL_SECONDS && visit.getState() == VisitState.ACTIVE) {
			// 위 조건들을 모두 만족하면 "도착" 이벤트 확정
			// → Visit 엔티티에 도착시각(arrivedAt)을 기록
			visit.arriveAt(recordedAt);
        }

        return new GeoFenceEvaluationResult(visit.getState(), dwellSeconds, accuracyPaused);
    }

}
