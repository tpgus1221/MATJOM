package com.matjom.matjom.visit.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.matjom.matjom.common.entity.BaseEntity;
import com.matjom.matjom.place.entity.Place;
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
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.Getter;

@Entity
@Table(name = "visits", indexes = {
	// [인덱스/액세스 패턴]
	// - user_id: "사용자별 ACTIVE 1개" 검사 및 최근 세션 조회 가속.
	// - place_id: 매장 대시보드가 "현재 방문 중 사용자"를 조회할 때 사용.
	// - state: 상태별 카운팅/필터링(ARRIVED, ACTIVE 등) 최적화.
	// - started_at: 기간별 리포팅/정렬(최근 세션)용.
	@Index(name = "idx_visits_user", columnList = "user_id"),
	@Index(name = "idx_visits_place", columnList = "place_id"),
	@Index(name = "idx_visits_state", columnList = "state"),
	@Index(name = "idx_visits_started_at", columnList = "started_at")
})
@Getter
public class Visit extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "visit_id")
	// [세션 식별자] 위치 이벤트/도착 확정/이력 기록의 기준 키
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", columnDefinition = "uuid", nullable = false)
	// [주체] 이 방문 세션의 사용자. "사용자별 ACTIVE 1개" 규칙의 기준
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
	// [대상] 방문하는 장소(지오펜스 중심). 거리 판정은 서비스/지오로직에서 수행
    private Place place;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
	// [상태] ACTIVE → ARRIVED/EXPIRED 등. 전이는 서비스가 비즈니스 규칙 하에 수행
    private VisitState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_mode", nullable = false, length = 20)
	// [수집] AUTO/MANUAL/NAVIGATION 등. 업로드 주기/UX 분석에 사용(서버는 기록 위주)
    private ClientMode clientMode;

    @Column(name = "started_at", nullable = false)
	// [시작 시각] 세션 생성 시 고정. 만료/체류시간 계산의 기준
    private OffsetDateTime startedAt;

    @Column(name = "arrived_at")
	// [도착 확정 시각] 수동/자동 공통. 일단 세팅되면 비가역(정책)
    private OffsetDateTime arrivedAt;

    @Column(name = "cancelled_at")
	// [취소 시각] 선택 정책. 취소 플로우 도입 시 사용
    private OffsetDateTime cancelledAt;

    @Column(name = "expired_at")
	// [만료 예정 시각] start + 30분 등. 만료 배치/트리거가 이 값을 근거로 상태 전이
    private OffsetDateTime expiredAt;

    @Column(name = "last_pos_at")
	// [최근 위치 시각] 최신 업로드 시각 스냅샷(대시보드/품질 분석용)
    private OffsetDateTime lastPositionAt;

    @Column(name = "dwell_started_at")
	// [체류 시작 시각] in-fence 연속 구간 시작점. 지오펜스 평가기가 관리
    private OffsetDateTime dwellStartedAt;

    @Column(name = "last_lat", precision = 9, scale = 6)
	// [최근 위도 스냅샷] 원시 이벤트의 요약본(목록/지도 표시·신속 조회)
    private BigDecimal lastLatitude;

    @Column(name = "last_lng", precision = 9, scale = 6)
	// [최근 경도 스냅샷]
    private BigDecimal lastLongitude;

    @Column(name = "last_accuracy_m", precision = 6, scale = 2)
	// [최근 정확도] 낮을수록 좋음. 정확도 낮을 때 dwell 제외 여부 판단의 힌트
    private BigDecimal lastAccuracyMeter;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta", columnDefinition = "jsonb")
	// [확장 메타] 실험 플래그/클라 정보/디버그 로그 등 가변 스키마 저장소
    private JsonNode meta;

    protected Visit() {
        // JPA
    }
    public Visit(User user,
                 Place place,
                 ClientMode clientMode,
                 OffsetDateTime startedAt) {
		// [불변식 체크] user/place/startedAt은 세션의 핵심 정체성 → null 금지
		this.user = Objects.requireNonNull(user, "user");
        this.place = Objects.requireNonNull(place, "place");
		// [모드 기본값] null이면 NAVIGATION으로 표준화(분석/리포팅에서 null 분기 제거)
		this.clientMode = clientMode == null ? ClientMode.NAVIGATION : clientMode;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
		// [초기 상태] 세션 생성은 항상 ACTIVE에서 시작(도착/만료 전 단계)
		this.state = VisitState.ACTIVE;
    }

    public void setExpiredAt(OffsetDateTime expiredAtValue) {
		// [세션 수명 고정] start 기준 계산된 만료 예정 시각 저장(예: +30m).
		//  - 만료 처리(상태 전이)는 서비스/배치가 수행
        this.expiredAt = expiredAtValue;
    }

    public void setMeta(JsonNode metaValue) {
		// [운영/실험 확장] 비핵심 스키마 필드를 JSONB로 저장(기능 플래그/디버그용 등)
		this.meta = metaValue;
    }

    public void updateLastPosition(BigDecimal latitudeValue,
                                   BigDecimal longitudeValue,
                                   BigDecimal accuracyValue,
                                   OffsetDateTime recordedAt) {
		// [최근 위치 스냅샷 갱신]
		//  - 지오펜스 평가 초기값, 대시보드 표시에 사용
		//  - 원시 이벤트는 VisitPosition에 별도로 영속됨(여긴 요약본)
        this.lastLatitude = latitudeValue;
        this.lastLongitude = longitudeValue;
        this.lastAccuracyMeter = accuracyValue;
        this.lastPositionAt = recordedAt;
    }

    public void startDwellIfAbsent(OffsetDateTime startedAtValue) {
		// [체류 시작 마킹] in-fence 진입 시점. 이미 세팅되어 있으면 유지(연속 구간 누적)
		if (dwellStartedAt == null) {
            dwellStartedAt = startedAtValue;
        }
    }

    public void resetDwell() {
		// [체류 초기화] out-fence로 이탈하거나 정확도 저하 등으로 누적 중단 시
		dwellStartedAt = null;
    }

    public void transitionTo(VisitState nextState) {
		// [상태 전이(일반)] 지오펜스/만료/취소 등 시스템 규칙에 의해 호출.
		//  - 유효성(예: ARRIVED→ACTIVE 금지)은 상위 서비스/도메인 규칙에서 보장
        if (nextState == null || state == nextState) {
            return;
        }
        state = nextState;
    }

    public void arriveAt(OffsetDateTime arrivedAtValue) {
		// [도착 확정(비가역)] ACTIVE → ARRIVED로 전이하고 시각을 기록.
		//  - 수동/자동 공통 진입점. 이후 역전이 불가(정책상)
        state = VisitState.ARRIVED;
        arrivedAt = arrivedAtValue;
    }

}
