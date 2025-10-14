package com.matjom.matjom.visit.entity;

import com.matjom.matjom.common.entity.BaseEntity;
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

import lombok.Getter;

@Entity
@Table(name = "visit_positions", indexes = {
	// [액세스 패턴 주석]
	// - visit_id: 세션별 위치 타임라인 조회(최근 n개, 세션 집계) 최적화.
	// - (visit_id, received_at): 세션 내 시간순 스캔/범위 질의(예: 최근 3분) 가속.
	@Index(name = "idx_visit_positions_visit", columnList = "visit_id"),
	@Index(name = "idx_visit_positions_visit_received", columnList = "visit_id, received_at")
})
@Getter
public class VisitPosition extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pos_id")
	// [샘플 식별자] 원시 위치 이벤트의 PK(정책상 수정/갱신하지 않고 '추가 전용')
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id", nullable = false)
	// [세션 참조] 어느 방문 세션에 속한 위치 샘플인지의 소속(지오펜스 평가 컨텍스트)
    private Visit visit;

    @Column(name = "lat", nullable = false, precision = 9, scale = 6)
	// [위도] 도 단위. precision=9, scale=6 → 소수점 6자리(≈ 0.11m 해상도)
	//  - 저장 전 반올림 정책은 서비스/DB 레벨에 일관되게 적용 필요
    private BigDecimal latitude;

    @Column(name = "lng", nullable = false, precision = 9, scale = 6)
	// [경도] 위와 동일(도 단위, 소수 6째 자리까지)
    private BigDecimal longitude;

    @Column(name = "accuracy_m", precision = 6, scale = 2)
	// [정확도(m)] 낮을수록 좋음. null 허용(클라이언트가 미제공할 수 있음)
	//  - 지오펜스/체류(dwell) 계산 시 "정확도 임계"를 못 넘으면 누적 제외/일시정지 처리의 근거
	private BigDecimal accuracyMeter;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 20)
	// [수집 모드] AUTO/MANUAL/NAVIGATION 등. 품질/성공률 분석 차원에서 보존
    private ClientMode mode;

    @Column(name = "received_at", nullable = false)
	// [수용 시각]
	//  - 일반적으로 "서버가 수신/저장한 시각"을 의미.
	//  - 현재 서비스 코드에선 '클라이언트 기록 시각'을 그대로 주입할 수 있어 의미 혼동 여지 존재.
	//    → 운영/리포트에서 서버수신시각 vs 클라이언트기록시각을 분리할 필요가 있으면 컬럼 분할 고려.
    private OffsetDateTime receivedAt;

    protected VisitPosition() {
        // JPA
    }

    public VisitPosition(Visit visit, BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeter, ClientMode mode, OffsetDateTime receivedAt) {
		// [불변식 가드(개념)] visit/위경도/mode/receivedAt은 샘플 의미의 핵심 → null 금지(DDL에서도 nullable=false 다수)
		this.visit = visit;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeter = accuracyMeter;
        this.mode = mode;
        this.receivedAt = receivedAt;
    }
}
