package com.matjom.matjom.visit.service;

import com.matjom.matjom.visit.repository.VisitReadRepository;
import com.matjom.matjom.visit.entity.VisitState;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VisitEligibilityChecker {

    private final VisitReadRepository visitReadRepository;

    public boolean isArrived(UUID userId, Long visitId) {
        return visitReadRepository.existsByIdAndUserIdAndState(visitId, userId, VisitState.ARRIVED);
    }

    public Optional<Long> findLatestArrivedVisitId(UUID userId, Long placeId) {
        return visitReadRepository.findLatestArrivedVisitId(userId, placeId);
    }

    public Optional<OffsetDateTime> findArrivedAt(UUID userId, Long visitId) {
        return visitReadRepository.findArrivedAtByIdAndUserIdAndState(visitId, userId, VisitState.ARRIVED);
    }
}
