package com.matjom.matjom.visit.repository;

import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitState;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitReadRepository extends JpaRepository<Visit, Long> {

    @Query("""
        SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END
        FROM Visit v
        WHERE v.id = :visitId
          AND v.user.id = :userId
          AND v.state = :state
          AND v.deletedAt IS NULL
    """)
    // 목적: ARRIVED 상태 방문이 실제 존재하는지 확인한다
    // 필요 이유: 리뷰/좋아요 자격 검증에서 전체 엔티티 조회 없이 빠르게 판정하기 위함이다
    // 로직: visitId·userId·state 조건을 모두 충족하는 행의 존재 여부를 Boolean으로 반환한다
    boolean existsByIdAndUserIdAndState(@Param("visitId") Long visitId,
                                        @Param("userId") UUID userId,
                                        @Param("state") VisitState state); // 9월 30일 최종: ARRIVED 여부만 판정하는 경량 쿼리

    // 목적: 가장 최근 ARRIVED 방문을 찾아 ID를 돌려준다
    // 필요 이유: 프런트에서 visitId를 넘겨주지 않아도 리뷰/좋아요를 처리할 수 있도록 한다
    // 로직: 사용자·장소·상태 조건을 만족하는 방문을 도착 시각 내림차순으로 조회해 첫 번째 ID를 반환한다
    Optional<Visit> findFirstByUser_IdAndPlace_IdAndStateAndDeletedAtIsNullOrderByArrivedAtDesc(UUID userId,
                                                                                              Long placeId,
                                                                                              VisitState state);

    default Optional<Long> findLatestArrivedVisitId(UUID userId, Long placeId) { // 9월 30일 최종: ARRIVED 방문 자동 매칭 용도
        return findFirstByUser_IdAndPlace_IdAndStateAndDeletedAtIsNullOrderByArrivedAtDesc(userId, placeId, VisitState.ARRIVED)
                .map(Visit::getId);
    }

    @Query("""
        SELECT v.arrivedAt FROM Visit v
        WHERE v.id = :visitId
          AND v.user.id = :userId
          AND v.state = :state
          AND v.deletedAt IS NULL
    """)
    Optional<OffsetDateTime> findArrivedAtByIdAndUserIdAndState(@Param("visitId") Long visitId,
                                                                @Param("userId") UUID userId,
                                                                @Param("state") VisitState state);

    @Query("""
        SELECT v FROM Visit v
        JOIN FETCH v.place p
        WHERE v.user.id = :userId
          AND v.state = :state
          AND v.deletedAt IS NULL
          AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY v.arrivedAt DESC NULLS LAST
    """)
    List<Visit> findArrivedVisits(@Param("userId") UUID userId,
                                  @Param("state") VisitState state,
                                  @Param("keyword") String keyword);
}
