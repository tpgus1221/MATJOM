package com.matjom.matjom.visit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjom.matjom.common.exception.base.SessionException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.idempotency.IdempotencyCallback;
import com.matjom.matjom.common.idempotency.IdempotencyResult;
import com.matjom.matjom.common.idempotency.IdempotencyStore;
import com.matjom.matjom.place.entity.Place;
import com.matjom.matjom.place.repository.PlaceJpaRepository;
import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;
import com.matjom.matjom.visit.dto.VisitManualArrivalRequest;
import com.matjom.matjom.visit.dto.VisitManualArrivalResponse;
import com.matjom.matjom.visit.dto.VisitSessionStartRequest;
import com.matjom.matjom.visit.dto.VisitSessionStartResponse;
import com.matjom.matjom.visit.entity.ClientMode;
import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitState;
import com.matjom.matjom.visit.entity.VisitStateEventSource;
import com.matjom.matjom.visit.repository.VisitRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
class VisitSessionServiceTest {

    @Mock
    private VisitRepository visitRepository;

    @Mock
    private PlaceJpaRepository placeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private IdempotencyStore idempotencyStore;

    @Mock
    private VisitStateTransitionRecorder stateTransitionRecorder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private VisitSessionService visitSessionService;

    @BeforeEach
    void setUp() {
        visitSessionService = new VisitSessionService(visitRepository, placeRepository, userRepository, idempotencyStore, objectMapper, stateTransitionRecorder);
    }

    @Test
    void startSessionCreatesNewVisit() {
        UUID userId = UUID.randomUUID();
        VisitSessionStartRequest request = new VisitSessionStartRequest();
        request.setPlaceId(10L);
        request.setClientMode(ClientMode.NAVIGATION);

        User user = new User("user@test.com", "tester", "password", AuthProvider.LOCAL);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Place place = org.mockito.Mockito.mock(Place.class);
        when(placeRepository.findById(10L)).thenReturn(Optional.of(place));

        when(visitRepository.existsByUser_IdAndState(userId, VisitState.ACTIVE)).thenReturn(false);
        when(visitRepository.save(any(Visit.class))).thenAnswer(new Answer<Visit>() {
            @Override
            public Visit answer(InvocationOnMock invocation) {
                Visit visit = invocation.getArgument(0);
                setVisitId(visit, 100L);
                return visit;
            }
        });

        when(idempotencyStore.replayOrRun(anyString(), anyString(), eq(VisitSessionStartResponse.class), any()))
                .thenAnswer(new Answer<IdempotencyResult<VisitSessionStartResponse>>() {
                    @Override
                    public IdempotencyResult<VisitSessionStartResponse> answer(InvocationOnMock invocation) {
                        IdempotencyCallback<VisitSessionStartResponse> callback = invocation.getArgument(3);
                        VisitSessionStartResponse response = callback.execute();
                        return new IdempotencyResult<>(response, false);
                    }
                });

        VisitSessionStartResponse response = visitSessionService.startSession(request, userId, "start-key");

        assertThat(response.sessionId()).isEqualTo(100L);
        assertThat(response.state()).isEqualTo(VisitState.ACTIVE);
        assertThat(response.replayed()).isFalse();
        verify(visitRepository).save(ArgumentMatchers.any(Visit.class));
    }

    @Test
    void startSessionThrowsWhenActiveExists() {
        UUID userId = UUID.randomUUID();
        VisitSessionStartRequest request = new VisitSessionStartRequest();
        request.setPlaceId(20L);

        User user = new User("user2@test.com", "tester", "password", AuthProvider.LOCAL);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Place place = org.mockito.Mockito.mock(Place.class);
        when(placeRepository.findById(20L)).thenReturn(Optional.of(place));
        when(visitRepository.existsByUser_IdAndState(userId, VisitState.ACTIVE)).thenReturn(true);

        when(idempotencyStore.replayOrRun(anyString(), anyString(), eq(VisitSessionStartResponse.class), any()))
                .thenAnswer(new Answer<IdempotencyResult<VisitSessionStartResponse>>() {
                    @Override
                    public IdempotencyResult<VisitSessionStartResponse> answer(InvocationOnMock invocation) {
                        IdempotencyCallback<VisitSessionStartResponse> callback = invocation.getArgument(3);
                        callback.execute();
                        return null;
                    }
                });

        boolean thrown = false;
        try {
            visitSessionService.startSession(request, userId, "dup-key");
        } catch (SessionException expected) {
            thrown = true;
            assertThat(expected.getErrorCode()).isEqualTo(ErrorCode.SESSION_ALREADY_EXISTS);
        }
        assertThat(thrown).isTrue();
    }

    @Test
    void startSessionReplayedResponseMarksFlag() {
        UUID userId = UUID.randomUUID();
        VisitSessionStartRequest request = new VisitSessionStartRequest();
        request.setPlaceId(30L);

        OffsetDateTime startedAt = OffsetDateTime.now();
        VisitSessionStartResponse cached = new VisitSessionStartResponse(5L, VisitState.ACTIVE, startedAt, startedAt.plusMinutes(30L), false);

        when(idempotencyStore.replayOrRun(anyString(), anyString(), eq(VisitSessionStartResponse.class), any()))
                .thenReturn(new IdempotencyResult<>(cached, true));

        VisitSessionStartResponse response = visitSessionService.startSession(request, userId, "replay-key");

        assertThat(response.sessionId()).isEqualTo(5L);
        assertThat(response.replayed()).isTrue();
        verify(visitRepository, never()).save(any(Visit.class));
    }

    @Test
    void confirmManualArrivalTransitionsToArrived() {
        OffsetDateTime startedAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(20L);
        User user = new User("user3@test.com", "tester", "password", AuthProvider.LOCAL);
        Place place = org.mockito.Mockito.mock(Place.class);
        when(place.getLatitude()).thenReturn(new BigDecimal("37.5665"));
        when(place.getLongitude()).thenReturn(new BigDecimal("126.9780"));
        Visit visit = new Visit(user, place, ClientMode.NAVIGATION, startedAt);
        setVisitId(visit, 200L);

        when(visitRepository.findById(200L)).thenReturn(Optional.of(visit));
        when(idempotencyStore.replayOrRun(anyString(), anyString(), eq(VisitManualArrivalResponse.class), any()))
                .thenAnswer(new Answer<IdempotencyResult<VisitManualArrivalResponse>>() {
                    @Override
                    public IdempotencyResult<VisitManualArrivalResponse> answer(InvocationOnMock invocation) {
                        IdempotencyCallback<VisitManualArrivalResponse> callback = invocation.getArgument(3);
                        VisitManualArrivalResponse response = callback.execute();
                        return new IdempotencyResult<>(response, false);
                    }
                });

        VisitManualArrivalRequest request = new VisitManualArrivalRequest();
        request.setLatitude(new BigDecimal("37.5665"));
        request.setLongitude(new BigDecimal("126.9780"));
        request.setRequestedBy("operator");

        VisitManualArrivalResponse response = visitSessionService.confirmManualArrival(200L, request, "arrival-key");

        assertThat(response.state()).isEqualTo(VisitState.ARRIVED);
        assertThat(response.replayed()).isFalse();
        assertThat(visit.getState()).isEqualTo(VisitState.ARRIVED);
        assertThat(visit.getArrivedAt()).isNotNull();
        verify(visitRepository).save(visit);
        verify(stateTransitionRecorder).record(eq(visit), eq(VisitState.ACTIVE), eq(VisitState.ARRIVED), eq(VisitStateEventSource.MANUAL_ARRIVAL), any(OffsetDateTime.class));
    }

    @Test
    void confirmManualArrivalAllowsExactlyTenMinutes() {
        ZoneId zoneId = ZoneId.of("Asia/Seoul");
        OffsetDateTime startedAt = OffsetDateTime.now(zoneId).minusSeconds(600L);
        User user = new User("user-ten@test.com", "tester", "password", AuthProvider.LOCAL);
        Place place = org.mockito.Mockito.mock(Place.class);
        when(place.getLatitude()).thenReturn(new BigDecimal("37.5665"));
        when(place.getLongitude()).thenReturn(new BigDecimal("126.9780"));
        Visit visit = new Visit(user, place, ClientMode.NAVIGATION, startedAt);
        setVisitId(visit, 205L);

        when(visitRepository.findById(205L)).thenReturn(Optional.of(visit));
        when(idempotencyStore.replayOrRun(anyString(), anyString(), eq(VisitManualArrivalResponse.class), any()))
                .thenAnswer(new Answer<IdempotencyResult<VisitManualArrivalResponse>>() {
                    @Override
                    public IdempotencyResult<VisitManualArrivalResponse> answer(InvocationOnMock invocation) {
                        IdempotencyCallback<VisitManualArrivalResponse> callback = invocation.getArgument(3);
                        VisitManualArrivalResponse response = callback.execute();
                        return new IdempotencyResult<>(response, false);
                    }
                });

        VisitManualArrivalRequest request = new VisitManualArrivalRequest();
        request.setLatitude(new BigDecimal("37.5665"));
        request.setLongitude(new BigDecimal("126.9780"));
        request.setRequestedBy("operator");

        VisitManualArrivalResponse response = visitSessionService.confirmManualArrival(205L, request, "arrival-ten");

        assertThat(response.state()).isEqualTo(VisitState.ARRIVED);
        assertThat(response.replayed()).isFalse();
        assertThat(visit.getState()).isEqualTo(VisitState.ARRIVED);
        assertThat(visit.getArrivedAt()).isNotNull();
        verify(visitRepository).save(visit);
        verify(stateTransitionRecorder).record(eq(visit), eq(VisitState.ACTIVE), eq(VisitState.ARRIVED), eq(VisitStateEventSource.MANUAL_ARRIVAL), any(OffsetDateTime.class));
    }

    @Test
    void confirmManualArrivalThrowsWhenOverSixtyMinutes() {
        ZoneId zoneId = ZoneId.of("Asia/Seoul");
        OffsetDateTime startedAt = OffsetDateTime.now(zoneId).minusSeconds(3601L);
        User user = new User("user-late@test.com", "tester", "password", AuthProvider.LOCAL);
        Place place = org.mockito.Mockito.mock(Place.class);
        when(place.getLatitude()).thenReturn(new BigDecimal("37.5665"));
        when(place.getLongitude()).thenReturn(new BigDecimal("126.9780"));
        Visit visit = new Visit(user, place, ClientMode.NAVIGATION, startedAt);
        setVisitId(visit, 206L);

        when(visitRepository.findById(206L)).thenReturn(Optional.of(visit));
        when(idempotencyStore.replayOrRun(anyString(), anyString(), eq(VisitManualArrivalResponse.class), any()))
                .thenAnswer(new Answer<IdempotencyResult<VisitManualArrivalResponse>>() {
                    @Override
                    public IdempotencyResult<VisitManualArrivalResponse> answer(InvocationOnMock invocation) {
                        IdempotencyCallback<VisitManualArrivalResponse> callback = invocation.getArgument(3);
                        callback.execute();
                        return null;
                    }
                });

        VisitManualArrivalRequest request = new VisitManualArrivalRequest();
        request.setLatitude(new BigDecimal("37.5665"));
        request.setLongitude(new BigDecimal("126.9780"));
        request.setRequestedBy("operator");

        boolean thrown = false;
        try {
            visitSessionService.confirmManualArrival(206L, request, "arrival-late");
        } catch (SessionException ex) {
            thrown = true;
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ARRIVAL_TIME_INVALID);
        }
        assertThat(thrown).isTrue();
        verify(stateTransitionRecorder, never()).record(eq(visit), any(VisitState.class), any(VisitState.class), any(VisitStateEventSource.class), any(OffsetDateTime.class));
    }

    @Test
    void confirmManualArrivalThrowsWhenDistanceExceeded() {
        OffsetDateTime startedAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(20L);
        User user = new User("user4@test.com", "tester", "password", AuthProvider.LOCAL);
        Place place = org.mockito.Mockito.mock(Place.class);
        when(place.getLatitude()).thenReturn(new BigDecimal("37.5665"));
        when(place.getLongitude()).thenReturn(new BigDecimal("126.9780"));
        Visit visit = new Visit(user, place, ClientMode.NAVIGATION, startedAt);
        setVisitId(visit, 201L);

        when(visitRepository.findById(201L)).thenReturn(Optional.of(visit));
        when(idempotencyStore.replayOrRun(anyString(), anyString(), eq(VisitManualArrivalResponse.class), any()))
                .thenAnswer(new Answer<IdempotencyResult<VisitManualArrivalResponse>>() {
                    @Override
                    public IdempotencyResult<VisitManualArrivalResponse> answer(InvocationOnMock invocation) {
                        IdempotencyCallback<VisitManualArrivalResponse> callback = invocation.getArgument(3);
                        callback.execute();
                        return null;
                    }
                });

        VisitManualArrivalRequest request = new VisitManualArrivalRequest();
        request.setLatitude(new BigDecimal("37.5700"));
        request.setLongitude(new BigDecimal("126.9900"));
        request.setRequestedBy("operator");

        boolean thrown = false;
        try {
            visitSessionService.confirmManualArrival(201L, request, "arrival-distance");
        } catch (SessionException ex) {
            thrown = true;
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ARRIVAL_DISTANCE_EXCEEDED);
        }
        assertThat(thrown).isTrue();
        verify(stateTransitionRecorder, never()).record(eq(visit), any(VisitState.class), any(VisitState.class), any(VisitStateEventSource.class), any(OffsetDateTime.class));
    }

    @Test
    void confirmManualArrivalThrowsWhenTimeInvalid() {
        OffsetDateTime startedAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(5L);
        User user = new User("user5@test.com", "tester", "password", AuthProvider.LOCAL);
        Place place = org.mockito.Mockito.mock(Place.class);
        when(place.getLatitude()).thenReturn(new BigDecimal("37.5665"));
        when(place.getLongitude()).thenReturn(new BigDecimal("126.9780"));
        Visit visit = new Visit(user, place, ClientMode.NAVIGATION, startedAt);
        setVisitId(visit, 202L);

        when(visitRepository.findById(202L)).thenReturn(Optional.of(visit));
        when(idempotencyStore.replayOrRun(anyString(), anyString(), eq(VisitManualArrivalResponse.class), any()))
                .thenAnswer(new Answer<IdempotencyResult<VisitManualArrivalResponse>>() {
                    @Override
                    public IdempotencyResult<VisitManualArrivalResponse> answer(InvocationOnMock invocation) {
                        IdempotencyCallback<VisitManualArrivalResponse> callback = invocation.getArgument(3);
                        callback.execute();
                        return null;
                    }
                });

        VisitManualArrivalRequest request = new VisitManualArrivalRequest();
        request.setLatitude(new BigDecimal("37.5665"));
        request.setLongitude(new BigDecimal("126.9780"));
        request.setRequestedBy("operator");

        boolean thrown = false;
        try {
            visitSessionService.confirmManualArrival(202L, request, "arrival-time");
        } catch (SessionException ex) {
            thrown = true;
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ARRIVAL_TIME_INVALID);
        }
        assertThat(thrown).isTrue();
        verify(stateTransitionRecorder, never()).record(eq(visit), any(VisitState.class), any(VisitState.class), any(VisitStateEventSource.class), any(OffsetDateTime.class));
    }

    @Test
    void confirmManualArrivalReturnsReplayedResponseWhenCached() {
        VisitManualArrivalResponse cached = new VisitManualArrivalResponse(300L, VisitState.ARRIVED, OffsetDateTime.now(), "operator", false);
        when(idempotencyStore.replayOrRun(anyString(), anyString(), eq(VisitManualArrivalResponse.class), any()))
                .thenReturn(new IdempotencyResult<>(cached, true));

        VisitManualArrivalRequest request = new VisitManualArrivalRequest();
        request.setLatitude(new BigDecimal("37.5665"));
        request.setLongitude(new BigDecimal("126.9780"));
        request.setRequestedBy("operator");

        VisitManualArrivalResponse response = visitSessionService.confirmManualArrival(300L, request, "arrival-replay");

        assertThat(response.replayed()).isTrue();
        verify(visitRepository, never()).findById(300L);
        verify(stateTransitionRecorder, never()).record(any(Visit.class), any(VisitState.class), any(VisitState.class), any(VisitStateEventSource.class), any(OffsetDateTime.class));
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
}
