package com.matjom.matjom.visit.repository;

import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitState;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VisitRepository extends JpaRepository<Visit, Long> {

    boolean existsByUser_IdAndState(UUID userId, VisitState state);

    Optional<Visit> findFirstByUser_IdAndState(UUID userId, VisitState state);

    @Query("SELECT v FROM Visit v WHERE v.state = :state AND v.startedAt <= :threshold ORDER BY v.startedAt ASC")
    List<Visit> findTimeoutCandidates(@Param("state") VisitState state,
                                      @Param("threshold") OffsetDateTime threshold,
                                      Pageable pageable);
}
