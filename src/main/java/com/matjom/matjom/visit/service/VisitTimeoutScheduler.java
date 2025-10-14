package com.matjom.matjom.visit.service;

import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitState;
import com.matjom.matjom.visit.entity.VisitStateEventSource;
import com.matjom.matjom.visit.repository.VisitRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class VisitTimeoutScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(VisitTimeoutScheduler.class);

    private final VisitRepository visitRepository;
    private final Clock clock;
    private final long timeoutMinutes;
    private final int batchSize;
    private final VisitStateTransitionRecorder stateTransitionRecorder;

    public VisitTimeoutScheduler(VisitRepository visitRepository,
                                 Clock clock,
                                 @Value("${visit.timeout.minutes:30}") long timeoutMinutes,
                                 @Value("${visit.timeout.batch-size:100}") int batchSize,
                                 VisitStateTransitionRecorder stateTransitionRecorder) {
        this.visitRepository = visitRepository;
        this.clock = clock;
        this.timeoutMinutes = timeoutMinutes;
        this.batchSize = batchSize;
        this.stateTransitionRecorder = Objects.requireNonNull(stateTransitionRecorder, "stateTransitionRecorder");
    }

    @Scheduled(fixedDelayString = "${visit.timeout.poll-interval-ms:60000}")
    @Transactional
    public void expireTimedOutVisits() {
        if (batchSize <= 0) {
            return;
        }

        int totalExpired = 0;
        OffsetDateTime threshold = OffsetDateTime.now(clock).minusMinutes(timeoutMinutes);
        List<Visit> candidates = fetchBatch(threshold);

        while (!candidates.isEmpty()) {
            OffsetDateTime expiredAt = OffsetDateTime.now(clock);
            for (int index = 0; index < candidates.size(); index++) {
                Visit visit = candidates.get(index);
                OffsetDateTime visitStart = visit.getStartedAt();
                if (visitStart != null && visitStart.isAfter(threshold)) {
                    continue;
                }
                VisitState previousState = visit.getState();
                if (visit.getState() == VisitState.ACTIVE) {
                    visit.transitionTo(VisitState.EXPIRED);
                    visit.setExpiredAt(expiredAt);
                    stateTransitionRecorder.record(visit, previousState, visit.getState(), VisitStateEventSource.TIMEOUT, expiredAt);
                }
            }
            visitRepository.saveAll(candidates);
            visitRepository.flush();
            totalExpired += candidates.size();
            threshold = OffsetDateTime.now(clock).minusMinutes(timeoutMinutes);
            candidates = fetchBatch(threshold);
        }

        if (totalExpired > 0) {
            LOGGER.info("Expired {} visit sessions that exceeded timeout threshold.", Integer.valueOf(totalExpired));
        }
    }

    private List<Visit> fetchBatch(OffsetDateTime threshold) {
        Pageable pageable = PageRequest.of(0, batchSize);
        List<Visit> result = visitRepository.findTimeoutCandidates(VisitState.ACTIVE, threshold, pageable);
        if (result == null) {
            return Collections.emptyList();
        }
        return result;
    }
}
