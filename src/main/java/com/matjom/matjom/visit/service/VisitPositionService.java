package com.matjom.matjom.visit.service;

import com.matjom.matjom.common.exception.base.SessionException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.visit.dto.VisitPositionRequest;
import com.matjom.matjom.visit.dto.VisitPositionResponse;
import com.matjom.matjom.visit.entity.ClientMode;
import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitPosition;
import com.matjom.matjom.visit.entity.VisitState;
import com.matjom.matjom.visit.entity.VisitStateEventSource;
import com.matjom.matjom.visit.geofence.GeoFenceEvaluationResult;
import com.matjom.matjom.visit.geofence.GeoFenceEvaluator;
import com.matjom.matjom.visit.repository.VisitPositionRepository;
import com.matjom.matjom.visit.repository.VisitRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitPositionService {

    private final VisitRepository visitRepository;
    private final VisitPositionRepository visitPositionRepository;
    private final GeoFenceEvaluator geoFenceEvaluator;
    private final VisitStateTransitionRecorder stateTransitionRecorder;

    public VisitPositionService(VisitRepository visitRepository,
                                VisitPositionRepository visitPositionRepository,
                                GeoFenceEvaluator geoFenceEvaluator,
                                VisitStateTransitionRecorder stateTransitionRecorder) {
        this.visitRepository = visitRepository;
        this.visitPositionRepository = visitPositionRepository;
        this.geoFenceEvaluator = geoFenceEvaluator;
        this.stateTransitionRecorder = Objects.requireNonNull(stateTransitionRecorder, "stateTransitionRecorder");
    }

    @Transactional
    public VisitPositionResponse recordPosition(Long sessionId, VisitPositionRequest request) {
		// [1] 세션 조회 — 이 위치 이벤트가 속할 방문 세션(Visit)을 로드한다.
		//     세션이 없으면 이후 모든 판정이 무의미하므로 즉시 404 성격의 예외.
		Optional<Visit> optionalVisit = visitRepository.findById(sessionId);
        if (optionalVisit.isEmpty()) {
            throw new SessionException(ErrorCode.SESSION_NOT_FOUND); // 세션 없으면 예외
        }

		Visit visit = optionalVisit.get();
		// [2] 세션 상태 검증 — 자동 판정은 ACTIVE 세션에서만 수행된다.
		//     ARRIVED/EXPIRED 등 비활성 상태에서의 위치 업로드는 제품상 허용하지 않음.
		VisitState previousState = visit.getState();
        if (visit.getState() != VisitState.ACTIVE) {
            throw new SessionException(ErrorCode.SESSION_ALREADY_INACTIVE); // 이미 비활성 상태면 기록 불가
        }

		// [3] 요청 파싱 — 클라이언트가 보낸 위치 샘플의 핵심 필드만 추출.
		//     accuracy는 지오펜스·체류 누적에 반영할지 여부(너무 큰 오차면 누적 제외)를 결정하는 근거가 된다.
		BigDecimal latitude = request.getLatitude();
        BigDecimal longitude = request.getLongitude();
        BigDecimal accuracy = request.getAccuracyMeters(); // GPS 정확도(m)
		ClientMode mode = request.modeOrDefault(); // 위치 수집 모드(AUTO/MANUAL 등)
        OffsetDateTime recordedAt = request.getRecordedAt(); // 위치 기록 시각

		// [4] 위치 엔티티 생성 — 이번 샘플을 영속화하기 위한 도메인 객체 구성.
		//     Visit와 연결하여 “어느 세션의 몇 번째 위치 이벤트인지”를 추적 가능하게 한다
        VisitPosition position = new VisitPosition(visit, latitude, longitude, accuracy, mode, recordedAt);

		// [5] 지오펜스 평가 — 비즈니스 핵심 로직.
		//   - evaluate는 (a) 현재 샘플이 반경 안/밖인지, (b) 정확도가 기준치 이상인지, (c) inFence 연속 누적 시간(dwell)을 계산한다.
		//   - 그 결과로 '이번 샘플을 반영했을 때의 다음 상태'를 반환(예: ACTIVE→ARRIVED 자동 전이 필요).
		//   - accuracy가 낮으면 dwell을 일시정지/제외(= 흔들림으로 인한 오탐 방지); 이 때 isAccuracyPaused=true.
		GeoFenceEvaluationResult evaluation = geoFenceEvaluator.evaluate(visit, position);

		// [6] 위치 저장 — 원시 샘플 자체는 항상 보관(사후 분석/분쟁 대응/품질 개선).
        VisitPosition saved = visitPositionRepository.save(position);

		// [7] Visit의 최신 위치 스냅샷 갱신 — 최근 좌표/정확도/시각을 세션 헤더에 반영.
		//     이는 다음 evaluate의 초기값·대시보드 표시에 사용된다.
		visit.updateLastPosition(latitude, longitude, accuracy, recordedAt);

		// [8] 상태 전이 — 평가 결과가 "다음 상태"를 제안할 수 있다.
		//   - resultingState가 ARRIVED라면 자동 도착이 확정된 것으로 간주하고 전이한다.
		//   - null이거나 현재와 동일하면 전이 없음(계속 ACTIVE).
		VisitState targetState = evaluation.getResultingState();
        if (targetState != null && targetState != visit.getState()) {
            visit.transitionTo(targetState);
        }

		// [9] 응답 구성용 꺼내기 — 프런트/모바일이 즉시 UI를 업데이트할 수 있도록 현재 판단치를 제공.
		//   - dwellSeconds: inFence로 인정된 연속 체류 누적(초). 임계치(예: 180s) 달성 시 ARRIVED 전이.
		//   - accuracyPaused: 이번 샘플이 낮은 정확도로 인해 dwell에 반영되지 않았음을 표시(UX 힌트: “GPS 신호 약함”).
		VisitState responseState = visit.getState();
        long dwellSeconds = evaluation.getDwellSeconds();
        boolean accuracyPaused = evaluation.isAccuracyPaused();

		// [10] 상태 전이 감사 — 상태가 바뀐 경우에만 이력 기록.
		//   - AUTO_ARRIVAL로 구분해 수동 도착과 리포팅에서 구별 가능.
		VisitState currentState = visit.getState();
        if (currentState != previousState) {
            VisitStateEventSource source = VisitStateEventSource.SYSTEM;
            if (currentState == VisitState.ARRIVED) {
                source = VisitStateEventSource.AUTO_ARRIVAL; // 자동 도착이면 구분 표시
            }
            stateTransitionRecorder.record(visit, previousState, currentState, source, recordedAt);
        }

		// [11] 응답 — 클라이언트가 다음 행동을 결정할 수 있도록 핵심 지표를 반환.
		//   - state가 ARRIVED면 클라는 위치 업로드 타이머를 중지하고, 도착 플로우(리뷰/혜택)로 전환.
		return new VisitPositionResponse(
                saved.getId(), // 방금 저장된 위치 ID
                visit.getId(), // 세션 ID
                responseState, // 세션 상태 (ACTIVE/ARRIVED 등)
                recordedAt, // 기록된 시각
                dwellSeconds, // 누적 체류 시간
                accuracyPaused // 이번 기록이 정확도 문제로 일시정지 되었는지 여부
        );
    }
}
