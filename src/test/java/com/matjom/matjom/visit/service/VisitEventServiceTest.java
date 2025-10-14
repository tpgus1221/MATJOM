package com.matjom.matjom.visit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.matjom.matjom.place.entity.Place;
import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.visit.entity.ClientMode;
import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitEvent;
import com.matjom.matjom.visit.entity.VisitEventType;
import com.matjom.matjom.visit.entity.VisitState;
import com.matjom.matjom.visit.repository.VisitEventRepository;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VisitEventServiceTest {

    @Mock
    private VisitEventRepository visitEventRepository;

    private VisitEventService visitEventService;

    @BeforeEach
    void setUp() {
        visitEventService = new VisitEventService(visitEventRepository, new ObjectMapper());
    }

    @Test
    void recordEventPersistsEntity() {
        Visit visit = createVisit();
        ObjectNode meta = visitEventService.createMetaNode();
        meta.put("key", "value");

        when(visitEventRepository.save(any(VisitEvent.class))).thenAnswer(new Answer<VisitEvent>() {
            @Override
            public VisitEvent answer(InvocationOnMock invocation) {
                VisitEvent event = invocation.getArgument(0);
                return event;
            }
        });

        VisitEvent saved = visitEventService.recordEvent(visit, VisitState.ACTIVE, VisitState.ARRIVED, VisitEventType.ARRIVED, OffsetDateTime.now(), meta);

        assertThat(saved.getEventType()).isEqualTo(VisitEventType.ARRIVED);
        verify(visitEventRepository).save(any(VisitEvent.class));
    }

    private Visit createVisit() {
        User user = new User("event@test.com", "tester", "pw", AuthProvider.LOCAL);
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
