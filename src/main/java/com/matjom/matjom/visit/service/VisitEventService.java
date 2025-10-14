package com.matjom.matjom.visit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitEvent;
import com.matjom.matjom.visit.entity.VisitEventType;
import com.matjom.matjom.visit.entity.VisitState;
import com.matjom.matjom.visit.repository.VisitEventRepository;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
//‘세션 상태 전이/중요 사건’을 감사 로그로 영구 기록
public class VisitEventService {

    private final VisitEventRepository visitEventRepository;
    private final ObjectMapper objectMapper;

    public VisitEventService(VisitEventRepository visitEventRepository,
                             ObjectMapper objectMapper) {
        this.visitEventRepository = Objects.requireNonNull(visitEventRepository, "visitEventRepository");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Transactional
    public VisitEvent recordEvent(Visit visit,
                                  VisitState fromState,
                                  VisitState toState,
                                  VisitEventType eventType,
                                  OffsetDateTime occurredAt,
                                  ObjectNode meta) {
		/*
		 * [비즈니스 목적]
		 * - 방문 세션(Visit)에서 발생한 "의미 있는 사건"을 1행 이벤트로 영구 기록한다(감사·리포트·리플레이 근거).
		 * - 전형적 호출 지점: 세션 시작(SESSION_STARTED), 자동 도착(ARRIVED), 수동 도착(ARRIVED_MANUAL),
		 *   만료(EXPIRED), 취소(CANCELLED), 특수 전이(STATE_CHANGED).
		 *
		 * [트랜잭션 경계]
		 * - 상태 전이(예: Visit.transitionTo)와 "같은 트랜잭션"에서 기록되어야 타임라인 불일치가 없다.
		 *   -> 호출자는 전이 직후 즉시 recordEvent를 호출할 것.
		 *
		 * [멱등/중복]
		 * - 이 메서드는 "단순 INSERT"만 수행한다. 중복 방지 책임은 호출자에게 있다.
		 *   예: 같은 사건을 2번 기록하지 않도록 (visitId, eventType, occurredAt) 자연키 관리 또는 상위 로직 멱등 적용.
		 *
		 * [meta(JSONB) 가이드]
		 * - 사건 컨텍스트를 표준 키로 담아 리포트/분석을 쉽게 한다.
		 *   예) ARRIVED: { "source":"AUTO", "distance":12.4, "accuracy":8.0, "dwellSec":185, "policy":{"radius":30,"dwell":180} }
		 *       ARRIVED_MANUAL: { "source":"MANUAL", "requestedBy":"user", "distance":9.7, "idempotencyKey":"..." }
		 *       EXPIRED: { "reason":"timeout", "ttlMinutes":30 }
		 */
        Objects.requireNonNull(visit, "visit");
        Objects.requireNonNull(fromState, "fromState");
        Objects.requireNonNull(toState, "toState");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");

		// [영속] 이벤트 엔티티 생성 → 추가(append). 수정/덮어쓰기 없음이 원칙.
		VisitEvent event = new VisitEvent(visit, eventType, fromState, toState, occurredAt, meta);
        return visitEventRepository.save(event);
    }

    public ObjectNode createMetaNode() {
		// [헬퍼] 빈 meta 객체 생성(호출자가 안전하게 필드 채워넣기 위함).
		//  - 표준 키 예시: source / distance / accuracy / dwellSec / policy{radius,dwell} / idempotencyKey / requestedBy
		return objectMapper.createObjectNode();
    }

    public JsonNode deepCopy(JsonNode node) {
		// [방어적 복사] 외부에서 전달된 JSON 트리를 복제하여 공유 참조로 인한 "사후 변조"를 방지.
		//  - 호출자가 동일 meta를 여러 이벤트에 재사용하거나, 이후 필드를 수정하더라도 저장된 레코드는 안전.
		if (node == null) {
            return null;
        }
        return node.deepCopy();
    }
}
