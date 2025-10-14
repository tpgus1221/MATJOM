package com.matjom.matjom.visit.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.matjom.matjom.common.entity.BaseEntity;
import com.matjom.matjom.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.Getter;

@Entity
@Table(name = "visit_events", indexes = {
	// [액세스 패턴 주석]
	// - (visit_id, occurred_at) 복합 인덱스: 특정 세션의 이벤트 타임라인을 시간순으로 빠르게 조회(감사/리플레이/디버깅).
	@Index(name = "idx_visit_events_visit", columnList = "visit_id, occurred_at")
})
@Getter
public class VisitEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
	// [이벤트 식별자] 단일 이벤트 레코드의 PK(비즈니스 키 아님)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id", nullable = false)
	// [세션 참조] 어떤 방문 세션에서 발생한 이벤트인지의 소속(감사·리포팅의 기준 축)
    private Visit visit;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
	// [스냅샷] 이벤트 당시 주체 사용자 ID. Visit.user의 스냅샷을 별도로 보관(조인 없이 집계/검색 가능)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
	// [이벤트 분류] 예: SESSION_STARTED, AUTO_ARRIVAL, MANUAL_ARRIVAL, EXPIRED, CANCELLED 등 도메인 시그널
    private VisitEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", nullable = false, length = 20)
	// [전이 전 상태] 이벤트 직전 Visit의 상태(감사·무결성 체크: 타임라인의 연속성 보장 근거)
    private VisitState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false, length = 20)
	// [전이 후 상태] 이벤트 적용 결과의 Visit 상태(비가역·허용 전이 규칙 검증용)
    private VisitState toState;

    @Column(name = "occurred_at", nullable = false)
	// [발생 시각] 이벤트가 효력을 갖는 시점(정렬/리플레이 기준; 보통 서버 시각)
    private OffsetDateTime occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta", columnDefinition = "jsonb")
	// [컨텍스트 메타] 원인/파라미터 스냅샷(예: 거리, 정확도, 요청자, 멱등키, 정책버전 등 가변 정보)
    private JsonNode meta;

    protected VisitEvent() {
        // JPA
    }

    public VisitEvent(Visit visit,
                      VisitEventType eventType,
                      VisitState fromState,
                      VisitState toState,
                      OffsetDateTime occurredAt,
                      JsonNode meta) {
		// [불변식] visit, eventType, 상태·시각은 이벤트 의미의 핵심 → null 금지
		this.visit = Objects.requireNonNull(visit, "visit");

		// [주체 스냅샷] 조회·집계를 위해 Visit.user를 즉시 UUID로 복제 저장(나중에 사용자 삭제/변경과 무관하게 감사 가능)
		User visitUser = visit.getUser();
        if (visitUser == null) {
            throw new IllegalArgumentException("visit user must not be null");
        }
        this.userId = visitUser.getId();
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.fromState = Objects.requireNonNull(fromState, "fromState");
        this.toState = Objects.requireNonNull(toState, "toState");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.meta = meta; // [선택] 컨텍스트가 있을 때만 세팅(없으면 null 허용)
    }
}
