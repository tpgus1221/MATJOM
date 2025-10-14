package com.matjom.matjom.visit.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitEventType;
import com.matjom.matjom.visit.entity.VisitState;
import com.matjom.matjom.visit.entity.VisitStateEventSource;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class VisitStateTransitionRecorder {

	// 상태 변화 이벤트를 기록(로그/DB 저장)하는 서비스
    private final VisitEventService visitEventService;

	// 상태 변화 시 특권(예: 검색 쿼터 초기화 등) 처리를 담당하는 서비스
    private final VisitPrivilegeService visitPrivilegeService;

	// 생성자 주입: 반드시 필요한 서비스이므로 null 방어 코드 포함
    public VisitStateTransitionRecorder(VisitEventService visitEventService,
                                        VisitPrivilegeService visitPrivilegeService) {
        this.visitEventService = Objects.requireNonNull(visitEventService, "visitEventService");
        this.visitPrivilegeService = Objects.requireNonNull(visitPrivilegeService, "visitPrivilegeService");
    }

	/**
	 * 상태 전환을 기록하는 메서드
	 *
	 * @param visit       상태가 바뀐 Visit 세션
	 * @param fromState   이전 상태
	 * @param toState     새 상태
	 * @param source      상태 전환 원인 (AUTO_ARRIVAL, TIMEOUT 등)
	 * @param occurredAt  상태 전환 발생 시각
	 */
    public void record(Visit visit,
                       VisitState fromState,
                       VisitState toState,
                       VisitStateEventSource source,
                       OffsetDateTime occurredAt) {
		// 필수 값이 없으면 기록하지 않고 종료
        if (visit == null || fromState == null || toState == null || occurredAt == null) {
            return;
        }
		// 상태가 바뀌지 않았다면 기록할 필요 없음
        if (fromState == toState) {
            return;
        }

		// 상태 + 원인 조합을 기반으로 이벤트 타입 판별
        VisitEventType eventType = resolveEventType(source, toState);
		// 이벤트 메타데이터(JSON) 생성
        ObjectNode meta = visitEventService.createMetaNode();
        meta.put("source", source == null ? "UNKNOWN" : source.name());

		// 이벤트 기록 (DB/로그에 남김)
        visitEventService.recordEvent(visit, fromState, toState, eventType, occurredAt, meta);

		// 도착(ARRIVED) 상태로 바뀌면, 검색 쿼터를 초기화하는 추가 동작 실행
        if (toState == VisitState.ARRIVED) {
            visitPrivilegeService.resetSearchQuotaForArrival(visit);
        }
    }

	/**
	 * 상태 + 원인 → 이벤트 타입 매핑
	 * (왜 이 이벤트가 발생했는지 더 구체적으로 남기기 위해)
	 */
    private VisitEventType resolveEventType(VisitStateEventSource source, VisitState toState) {
        if (source == VisitStateEventSource.MANUAL_ARRIVAL && toState == VisitState.ARRIVED) {
            return VisitEventType.ARRIVED_MANUAL; // 사용자가 직접 도착 체크
        }
        if (source == VisitStateEventSource.AUTO_ARRIVAL && toState == VisitState.ARRIVED) {
            return VisitEventType.ARRIVED; // 지오펜스로 자동 도착
        }
        if (source == VisitStateEventSource.TIMEOUT && toState == VisitState.EXPIRED) {
            return VisitEventType.EXPIRED; // 시간 초과로 만료
        }
        if (source == VisitStateEventSource.CANCEL && toState == VisitState.CANCELLED) {
            return VisitEventType.CANCELLED; // 사용자가 취소
        }
        if (source == VisitStateEventSource.SYSTEM && toState == VisitState.ACTIVE) {
            return VisitEventType.SESSION_STARTED; // 세션이 새로 시작됨
        }
        return VisitEventType.STATE_CHANGED; // 그 외 일반 상태 전환
    }
}
