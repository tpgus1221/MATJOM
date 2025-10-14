package com.matjom.matjom.visit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matjom.matjom.place.entity.Place;
import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.visit.entity.ClientMode;
import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitState;
import com.matjom.matjom.visit.entity.VisitStateEventSource;
import com.matjom.matjom.visit.repository.VisitRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VisitTimeoutSchedulerTest {

    @Mock
    private VisitRepository visitRepository;

    @Mock
    private VisitStateTransitionRecorder stateTransitionRecorder;

    private Clock fixedClock;

    private VisitTimeoutScheduler scheduler;

    @BeforeEach
    void setUp() {
        ZoneId zoneId = ZoneId.of("Asia/Seoul");
        OffsetDateTime fixedDateTime = OffsetDateTime.parse("2025-02-27T06:30:00+09:00");
        Instant fixedInstant = fixedDateTime.toInstant();
        fixedClock = Clock.fixed(fixedInstant, zoneId);
        scheduler = new VisitTimeoutScheduler(visitRepository, fixedClock, 30L, 2, stateTransitionRecorder);
    }

    @Test
    void expireTimedOutVisitsTransitionsStateAndSetsExpiredAt() {
        User user = new User("timeout@test.com", "tester", "password", AuthProvider.LOCAL);
        Place place = Mockito.mock(Place.class);
        OffsetDateTime startedAt = OffsetDateTime.now(fixedClock).minusSeconds(1805L);
        Visit visit = new Visit(user, place, ClientMode.NAVIGATION, startedAt);
        List<Visit> firstBatch = new ArrayList<Visit>();
        firstBatch.add(visit);

        when(visitRepository.findTimeoutCandidates(eq(VisitState.ACTIVE), any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(firstBatch)
                .thenReturn(Collections.<Visit>emptyList());
        when(visitRepository.saveAll(firstBatch)).thenReturn(firstBatch);

        scheduler.expireTimedOutVisits();

        assertThat(visit.getState()).isEqualTo(VisitState.EXPIRED);
        assertThat(visit.getExpiredAt()).isEqualTo(OffsetDateTime.now(fixedClock));
        verify(visitRepository).saveAll(firstBatch);
        verify(stateTransitionRecorder).record(eq(visit), eq(VisitState.ACTIVE), eq(VisitState.EXPIRED), eq(VisitStateEventSource.TIMEOUT), any(OffsetDateTime.class));
    }

    @Test
    void expireTimedOutVisitsSkipsWhenWithinThreshold() {
        User user = new User("threshold@test.com", "tester", "password", AuthProvider.LOCAL);
        Place place = Mockito.mock(Place.class);
        OffsetDateTime startedAt = OffsetDateTime.now(fixedClock).minusSeconds(1799L);
        Visit visit = new Visit(user, place, ClientMode.NAVIGATION, startedAt);
        List<Visit> batch = new ArrayList<Visit>();
        batch.add(visit);

        when(visitRepository.findTimeoutCandidates(eq(VisitState.ACTIVE), any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(batch)
                .thenReturn(Collections.<Visit>emptyList());
        when(visitRepository.saveAll(batch)).thenReturn(batch);

        scheduler.expireTimedOutVisits();

        assertThat(visit.getState()).isEqualTo(VisitState.ACTIVE);
        assertThat(visit.getExpiredAt()).isNull();
        verify(stateTransitionRecorder, never()).record(any(Visit.class), any(VisitState.class), any(VisitState.class), any(VisitStateEventSource.class), any(OffsetDateTime.class));
    }

    @Test
    void expireTimedOutVisitsSkipsWhenNoCandidates() {
        when(visitRepository.findTimeoutCandidates(eq(VisitState.ACTIVE), any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(Collections.<Visit>emptyList());

        scheduler.expireTimedOutVisits();

        verify(visitRepository, never()).saveAll(anyList());
        verify(stateTransitionRecorder, never()).record(any(Visit.class), any(VisitState.class), any(VisitState.class), any(VisitStateEventSource.class), any(OffsetDateTime.class));
    }
}
