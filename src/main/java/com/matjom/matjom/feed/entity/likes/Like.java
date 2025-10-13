package com.matjom.matjom.feed.entity.likes;

import com.matjom.matjom.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "daily_likes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Like extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "visit_id", nullable = false, unique = true)
    private Long visitId;

    @Column(name = "date_kst", nullable = false)
    private LocalDate dateKst;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private LikeStatus status = LikeStatus.ACTIVE;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    public boolean isActive() {
        return status == LikeStatus.ACTIVE && !isDeleted();
    }

    public void cancel(OffsetDateTime cancelledAt) {
        this.status = LikeStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
    }

    public void reactivate() {
        this.status = LikeStatus.ACTIVE;
        this.cancelledAt = null;
    }
}
