package com.matjom.matjom.visit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matjom.matjom.common.exception.base.SessionException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.visit.dto.VisitPositionRequest;
import com.matjom.matjom.visit.dto.VisitPositionResponse;
import com.matjom.matjom.visit.entity.ClientMode;
import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitPosition;
import com.matjom.matjom.visit.entity.VisitState;
import com.matjom.matjom.visit.entity.VisitStateEventSource;
import com.matjom.matjom.visit.geofence.GeoFenceEvaluationResult;
import com.matjom.matjom.visit.geofence.GeoFenceEvaluator;
import com.matjom.matjom.visit.repository.VisitPositionRepository;
import com.matjom.matjom.visit.repository.VisitRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VisitPositionServiceTest {

    @Mock
    private VisitRepository visitRepository;

    @Mock
    private VisitPositionRepository visitPositionRepository;

    @Mock
    private GeoFenceEvaluator geoFenceEvaluator;

    @Mock
    private VisitStateTransitionRecorder stateTransitionRecorder;

    private VisitPositionService visitPositionService;

    @BeforeEach
    void setUp() {
        visitPositionService = new VisitPositionService(visitRepository, visitPositionRepository, geoFenceEvaluator, stateTransitionRecorder);
    }

    @Test
    void recordPositionSavesEntityAndReturnsResponse() throws Exception {
        Visit visit = createVisit();
        setVisitId(visit, 11L);
        when(visitRepository.findById(11L)).thenReturn(Optional.of(visit));

        when(visitPositionRepository.save(any(VisitPosition.class))).thenAnswer(new Answer<VisitPosition>() {
            @Override
            public VisitPosition answer(InvocationOnMock invocation) {
                VisitPosition position = invocation.getArgument(0);
                setPositionId(position, 99L);
                return position;
            }
        });

        when(geoFenceEvaluator.evaluate(any(Visit.class), any(VisitPosition.class)))
                .thenReturn(new GeoFenceEvaluationResult(VisitState.ACTIVE, 0L, false));

        VisitPositionRequest request = new VisitPositionRequest();
        request.setLatitude(new BigDecimal("37.5665"));
        request.setLongitude(new BigDecimal("126.9780"));
        request.setAccuracyMeters(new BigDecimal("5.5"));
        request.setMode(ClientMode.NAVIGATION);
        OffsetDateTime recordedAt = OffsetDateTime.now();
        request.setRecordedAt(recordedAt);

        VisitPositionResponse response = visitPositionService.recordPosition(11L, request);

        assertThat(response.positionId()).isEqualTo(99L);
        assertThat(response.sessionId()).isEqualTo(11L);
        assertThat(response.state()).isEqualTo(VisitState.ACTIVE);
        assertThat(response.recordedAt()).isEqualTo(recordedAt);
        verify(stateTransitionRecorder, never()).record(any(Visit.class), any(VisitState.class), any(VisitState.class), any(VisitStateEventSource.class), any(OffsetDateTime.class));
    }

    @Test
    void recordPositionThrowsWhenSessionNotFound() {
        when(visitRepository.findById(42L)).thenReturn(Optional.empty());

        VisitPositionRequest request = new VisitPositionRequest();
        request.setLatitude(new BigDecimal("37.5"));
        request.setLongitude(new BigDecimal("127.0"));
        request.setRecordedAt(OffsetDateTime.now());

        boolean thrown = false;
        try {
            visitPositionService.recordPosition(42L, request);
        } catch (SessionException ex) {
            thrown = true;
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SESSION_NOT_FOUND);
        }
        assertThat(thrown).isTrue();
        verify(stateTransitionRecorder, never()).record(any(Visit.class), any(VisitState.class), any(VisitState.class), any(VisitStateEventSource.class), any(OffsetDateTime.class));
    }

    @Test
    void recordPositionThrowsWhenSessionInactive() {
        Visit visit = createVisit();
        visit.transitionTo(VisitState.ARRIVED);
        setVisitId(visit, 12L);
        when(visitRepository.findById(12L)).thenReturn(Optional.of(visit));

        VisitPositionRequest request = new VisitPositionRequest();
        request.setLatitude(new BigDecimal("37.5"));
        request.setLongitude(new BigDecimal("127.0"));
        request.setRecordedAt(OffsetDateTime.now());

        boolean thrown = false;
        try {
            visitPositionService.recordPosition(12L, request);
        } catch (SessionException ex) {
            thrown = true;
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SESSION_ALREADY_INACTIVE);
        }
        assertThat(thrown).isTrue();
        verify(stateTransitionRecorder, never()).record(any(Visit.class), any(VisitState.class), any(VisitState.class), any(VisitStateEventSource.class), any(OffsetDateTime.class));
    }

    @Test
    void recordPositionUpdatesStateWhenEvaluatorSuggestsChange() throws Exception {
        Visit visit = createVisit();
        setVisitId(visit, 13L);
        when(visitRepository.findById(13L)).thenReturn(Optional.of(visit));

        when(visitPositionRepository.save(any(VisitPosition.class))).thenAnswer(new Answer<VisitPosition>() {
            @Override
            public VisitPosition answer(InvocationOnMock invocation) {
                VisitPosition position = invocation.getArgument(0);
                setPositionId(position, 200L);
                return position;
            }
        });

        when(geoFenceEvaluator.evaluate(any(Visit.class), any(VisitPosition.class)))
                .thenReturn(new GeoFenceEvaluationResult(VisitState.ARRIVED, 185L, false));

        VisitPositionRequest request = new VisitPositionRequest();
        request.setLatitude(new BigDecimal("37.6"));
        request.setLongitude(new BigDecimal("127.1"));
        request.setRecordedAt(OffsetDateTime.now());

        VisitPositionResponse response = visitPositionService.recordPosition(13L, request);

        assertThat(response.state()).isEqualTo(VisitState.ARRIVED);
        assertThat(response.dwellSeconds()).isEqualTo(185L);
        verify(stateTransitionRecorder).record(eq(visit), eq(VisitState.ACTIVE), eq(VisitState.ARRIVED), eq(VisitStateEventSource.AUTO_ARRIVAL), any(OffsetDateTime.class));
    }

    private Visit createVisit() {
        UUID userId = UUID.randomUUID();
        com.matjom.matjom.user.entity.User user = new com.matjom.matjom.user.entity.User("user@test.com", "tester", "password", com.matjom.matjom.user.entity.AuthProvider.LOCAL);
        com.matjom.matjom.place.entity.Place place = org.mockito.Mockito.mock(com.matjom.matjom.place.entity.Place.class);
        return new Visit(user, place, ClientMode.NAVIGATION, OffsetDateTime.now());
    }

    private void setVisitId(Visit visit, Long id) {
        try {
            Field field = Visit.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(visit, id);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new IllegalStateException("visit id 설정에 실패했습니다.", ex);
        }
    }

    private void setPositionId(VisitPosition position, Long id) {
        try {
            Field field = VisitPosition.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(position, id);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new IllegalStateException("position id 설정에 실패했습니다.", ex);
        }
    }
}
