package com.matjom.matjom.visit.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.matjom.matjom.place.entity.Place;
import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.visit.entity.ClientMode;
import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitEventType;
import com.matjom.matjom.visit.entity.VisitState;
import com.matjom.matjom.visit.entity.VisitStateEventSource;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VisitStateTransitionRecorderTest {

    @Mock
    private VisitEventService visitEventService;

    @Mock
    private VisitPrivilegeService visitPrivilegeService;

    private VisitStateTransitionRecorder recorder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        recorder = new VisitStateTransitionRecorder(visitEventService, visitPrivilegeService);
    }

    @Test
    void recordSkipsWhenStateUnchanged() {
        Visit visit = createVisit();
        recorder.record(visit, VisitState.ACTIVE, VisitState.ACTIVE, VisitStateEventSource.SYSTEM, OffsetDateTime.now());
        verify(visitEventService, never()).recordEvent(any(Visit.class), any(VisitState.class), any(VisitState.class), any(VisitEventType.class), any(OffsetDateTime.class), any(ObjectNode.class));
        verify(visitPrivilegeService, never()).resetSearchQuotaForArrival(any(Visit.class));
    }

    @Test
    void recordArrivalLogsEventAndResetsPrivileges() {
        Visit visit = createVisit();
        when(visitEventService.createMetaNode()).thenReturn(objectMapper.createObjectNode());

        recorder.record(visit, VisitState.ACTIVE, VisitState.ARRIVED, VisitStateEventSource.AUTO_ARRIVAL, OffsetDateTime.now());

        verify(visitEventService).recordEvent(any(Visit.class), any(VisitState.class), any(VisitState.class), any(VisitEventType.class), any(OffsetDateTime.class), any(ObjectNode.class));
        verify(visitPrivilegeService).resetSearchQuotaForArrival(visit);
    }

    private Visit createVisit() {
        User user = new User("recorder@test.com", "tester", "pw", AuthProvider.LOCAL);
        setUserId(user, UUID.randomUUID());
        Place place = org.mockito.Mockito.mock(Place.class);
        return new Visit(user, place, ClientMode.NAVIGATION, OffsetDateTime.now());
    }

    private void setUserId(User user, UUID id) {
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new IllegalStateException("user id 설정에 실패했습니다.", ex);
        }
    }
}
