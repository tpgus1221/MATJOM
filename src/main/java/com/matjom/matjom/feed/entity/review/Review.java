package com.matjom.matjom.feed.entity.review;

import com.matjom.matjom.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "visit_id", nullable = false, unique = true)
    private Long visitId;

    @Column(name = "user_name", nullable = false, length = 50)
    private String userName;

    @Column(name = "text", nullable = false, length = 140)
    @Setter
    private String text;

    // 목적: 리뷰가 삭제되지 않은 상태인지 확인한다
    // 필요 이유: 소프트 삭제를 사용하는 만큼 표시 여부를 빠르게 판단해야 한다
    // 로직: BaseEntity의 isDeleted 값을 반대로 반환한다
    public boolean isActive() { // 9월 26일 최종: 심플한 활성 여부 판단
        return !isDeleted();
    }
}
