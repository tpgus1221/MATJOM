package com.matjom.matjom.visit.entity;

import com.matjom.matjom.common.entity.BaseEntity;
import com.matjom.matjom.place.entity.Place;
import com.matjom.matjom.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

import lombok.Getter;

@Entity
@Getter
@Table(name = "user_place_first_arrivals")
public class UserPlaceFirstArrival extends BaseEntity {

    @EmbeddedId
	// [복합 PK] (user_id, place_id). "사용자-장소" 쌍당 레코드 1개를 보장(최초 도착만 기록)
    private UserPlaceFirstArrivalId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", columnDefinition = "uuid", nullable = false)
	// [소유자] 최초 도착의 주체 사용자. PK의 user_id와 동기화(MapsId)
    private User user;

    @MapsId("placeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
	// [대상 장소] PK의 place_id와 동기화(MapsId)
    private Place place;

    @Column(name = "first_arrived_at", nullable = false)
	// [기준 시각] "해당 사용자가 그 장소에 처음 도착이 확정된" 시각(서버 기준). 이후 수정/갱신하지 않음(불변)
    private OffsetDateTime firstArrivedAt;

    protected UserPlaceFirstArrival() {
        // JPA
    }

    public UserPlaceFirstArrival(User user, Place place, OffsetDateTime firstArrivedAt)
	{
		// [생성 규칙]
		// - 반드시 Visit 상태가 ARRIVED로 전이된 직후에만 생성(수동/자동 공통).
		// - 이미 존재하면(동일 user/place) 재생성 금지 → 서비스 계층에서 UPSERT 또는 제약 예외 캐치로 보장.
        this.id = new UserPlaceFirstArrivalId(user.getId(), place.getId());
        this.user = user;
        this.place = place;
        this.firstArrivedAt = firstArrivedAt;
    }
}
