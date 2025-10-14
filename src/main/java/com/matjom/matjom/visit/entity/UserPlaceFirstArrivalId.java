package com.matjom.matjom.visit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

@Embeddable
@Getter
public class UserPlaceFirstArrivalId implements Serializable {

    @Column(name = "user_id", columnDefinition = "uuid")
	// [복합 PK-1] 사용자 키(UUID). 삭제/탈퇴와 무관하게 "첫 방문" 사실을 보존하기 위한 스냅샷
    private UUID userId;

    @Column(name = "place_id")
	// [복합 PK-2] 장소 키. 장소 병합/폐점 시 이력 유지 여부는 제품 정책(보통 보존)
    private Long placeId;

    protected UserPlaceFirstArrivalId() {
    }

    public UserPlaceFirstArrivalId(UUID userId, Long placeId) {
		// [불변식] 두 키가 모두 있어야 유효한 식별자
        this.userId = userId;
        this.placeId = placeId;
    }

    @Override
    public boolean equals(Object o) {
		// [동치성] 복합키 값 동등성으로 동일 레코드 판별(영속성 컨텍스트/컬렉션 키로 사용)
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserPlaceFirstArrivalId that = (UserPlaceFirstArrivalId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(placeId, that.placeId);
    }

    @Override
    public int hashCode() {
		// [해시 일관성] 컬렉션/캐시 키로 안전하게 사용하기 위한 해시 구현
        return Objects.hash(userId, placeId);
    }
}
