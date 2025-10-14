package com.matjom.matjom.visit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matjom.matjom.place.entity.Place;
import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.visit.entity.ClientMode;
import com.matjom.matjom.visit.entity.Visit;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VisitPrivilegeServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private VisitPrivilegeService visitPrivilegeService;

    @BeforeEach
    void setUp() {
        visitPrivilegeService = new VisitPrivilegeService(stringRedisTemplate);
    }

    @Test
    void resetSearchQuotaDeletesUserKey() {
        Visit visit = createVisitWithUser();
        UUID userId = visit.getUser().getId();
        when(stringRedisTemplate.delete(Collections.singletonList("rl:places:user:" + userId))).thenReturn(1L);

        visitPrivilegeService.resetSearchQuotaForArrival(visit);

        verify(stringRedisTemplate).delete(Collections.singletonList("rl:places:user:" + userId));
    }

    @Test
    void resetSearchQuotaSkipsWhenUserMissing() {
        Visit visit = createVisitWithoutUserId();
        visitPrivilegeService.resetSearchQuotaForArrival(visit);
        verify(stringRedisTemplate, never()).delete(anyCollection());
    }

    @Test
    void resetSearchQuotaPropagatesRedisFailure() {
        Visit visit = createVisitWithUser();
        UUID userId = visit.getUser().getId();
        when(stringRedisTemplate.delete(Collections.singletonList("rl:places:user:" + userId))).thenThrow(new IllegalStateException("redis error"));

        boolean thrown = false;
        try {
            visitPrivilegeService.resetSearchQuotaForArrival(visit);
        } catch (IllegalStateException ex) {
            thrown = true;
            assertThat(ex.getMessage()).contains("redis error");
        }
        assertThat(thrown).isTrue();
    }

    private Visit createVisitWithUser() {
        User user = new User("user@test.com", "tester", "pw", AuthProvider.LOCAL);
        setUserId(user, UUID.randomUUID());
        Place place = org.mockito.Mockito.mock(Place.class);
        return new Visit(user, place, ClientMode.NAVIGATION, OffsetDateTime.now());
    }

    private Visit createVisitWithoutUserId() {
        User user = new User("user@test.com", "tester", "pw", AuthProvider.LOCAL);
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
