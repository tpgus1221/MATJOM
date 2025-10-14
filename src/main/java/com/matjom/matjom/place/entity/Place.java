package com.matjom.matjom.place.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.matjom.matjom.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import lombok.Getter;
import lombok.Setter;

@SuppressWarnings("unused")
@Entity
@Getter
// 주의: 공간 인덱스(GiST)는 @Index로 생성되지 않습니다. 아래 주석의 DDL을 운용에 반영하세요.
@Table(name = "places", indexes = {
        @Index(name = "idx_places_provider_id", columnList = "provider_id"),
        @Index(name = "idx_places_addr_sido", columnList = "addr_sido"),
        @Index(name = "idx_places_addr_sigungu", columnList = "addr_sigungu"),
        @Index(name = "idx_places_addr_eupmyeondong", columnList = "addr_eupmyeondong")
})
public class Place extends BaseEntity {
	// ====== 비즈니스 상수 & 헬퍼 ======
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

	// ====== 식별자 ======
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_id")
    private Long id; // PK는 자동 증가(INT 혹은 BIGINT)

	// ====== 표시/검색 기본 필드 ======
	@Column(name = "name", nullable = false, length = 100)
	private String name; // 장소명, 널 금지, 최대 100자

	@Column(name = "lat", nullable = false, precision = 9, scale = 6)
	private BigDecimal latitude; // 위도: 소수점 6자리까지 저장

	@Column(name = "lng", nullable = false, precision = 9, scale = 6)
	private BigDecimal longitude; // 경도: precision/scale 동일

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "category", columnDefinition = "text[]", nullable = false)
	private String[] category; // Postgres text[]와 매핑

	@Column(name = "provider_id", nullable = false, length = 100)
    private String providerId; // 외부 데이터 공급자 고유 ID

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber; // 대표 연락처

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "working_hours", columnDefinition = "jsonb", nullable = false)
    private JsonNode workingHours; // 영업시간 JSONB

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "break_time", columnDefinition = "jsonb", nullable = false)
    private JsonNode breakTime; // 브레이크타임 JSONB

    @Column(name = "opened_at", nullable = false)
    private LocalDate openedAt; // 개업일

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "opened_at_source", columnDefinition = "jsonb", nullable = false)
    private JsonNode openedAtSource; // 개업일 출처 메타

    @Column(name = "biz_status", nullable = false, length = 20)
    private String bizStatus; // 영업 상태(정상, 휴업 등)

    @Column(name = "addr_sido", nullable = false, length = 20)
    private String addrSido; // 시/도

    @Column(name = "addr_sigungu", nullable = false, length = 30)
    private String addrSigungu; // 시/군/구

    @Column(name = "addr_eupmyeondong", nullable = false, length = 80)
    private String addrEupmyeondong; // 읍/면/동

    @Column(name = "addr_street", nullable = false, length = 100)
			private String addrStreet; // 도로명

    @Column(name = "addr_detail", nullable = false, length = 100)
    private String addrDetail; // 상세 주소

	// ====== 지오 스패셜 정본(SSOT) ======
	// [핵심 비즈니스 결정]
	// - 정렬/커서/반경 판단은 **절대거리(m)** 기준이므로 PostGIS의 geography 타입을 사용.
	// - geometry(4326)로도 구현 가능하지만, meter 일관성 확보 위해 geography 채택.
	// - Hibernate 6.x: @JdbcTypeCode(SqlTypes.GEOMETRY) + columnDefinition으로 DB 타입을 정확히 지정.
    @Column(name = "location", columnDefinition = "geography(Point,4326)")
    private Point location; // 좌표는 반드시 (lng, lat) 순서, SRID=4326

    protected Place() {
        // JPA
    }

    @PrePersist
    @PreUpdate
    void updateLocation() {
        if (latitude != null && longitude != null) {
            this.location = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude.doubleValue(), latitude.doubleValue()));
        }
    }

    public List<String> getCategory() {
		// 내부 배열을 변경 불가 리스트로 복사해 외부에서 불변성 유지
        return category == null ? List.of() : List.copyOf(Arrays.asList(category.clone()));
    }

    public void setCategory(List<String> category) {
		// 외부에서 List로 전달하면 배열로 변환하여 저장 (NULL 허용)
        this.category = category == null ? null : category.toArray(new String[0]);
    }
}
