package com.matjom.matjom.visit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.matjom.matjom.visit.dto.VisitListResponseDTO;
import com.matjom.matjom.feed.entity.likes.Like;
import com.matjom.matjom.feed.entity.review.Review;
import com.matjom.matjom.feed.repository.LikeRepository;
import com.matjom.matjom.feed.repository.ReviewRepository;
import com.matjom.matjom.visit.repository.VisitReadRepository;
import com.matjom.matjom.place.entity.Place;
import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitState;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VisitHistoryServiceTest {

    @Mock
    private VisitReadRepository visitReadRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private LikeRepository likeRepository;

    @InjectMocks
    private VisitHistoryService visitHistoryService;

    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    @DisplayName("24시간 내 작성 가능한 방문을 최상단으로 정렬한다")
    void reorderPlacesPendingFirst() {
        Visit visit1 = buildVisit(1L, "Alpha", OffsetDateTime.now().minusHours(3));
        Visit visit2 = buildVisit(2L, "Beta", OffsetDateTime.now().minusHours(1));
        Visit visit3 = buildVisit(3L, "Gamma", OffsetDateTime.now().minusHours(30));
        given(visitReadRepository.findArrivedVisits(USER_ID, VisitState.ARRIVED, null))
                .willReturn(List.of(visit3, visit1, visit2));

        Review review = Review.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .placeId(visit2.getPlace().getId())
                .visitId(visit2.getId())
                .userName("홍길동")
                .text("좋아요")
                .build();
        Like like = Like.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .placeId(visit2.getPlace().getId())
                .visitId(visit2.getId())
                .build();

        given(reviewRepository.findByVisitIdInAndDeletedAtIsNull(List.of(3L, 1L, 2L)))
                .willReturn(List.of(review));
        given(likeRepository.findByVisitIdInAndDeletedAtIsNull(List.of(3L, 1L, 2L)))
                .willReturn(List.of(like));

        VisitListResponseDTO response = visitHistoryService.getMyArrivedVisits(USER_ID, null);

        assertThat(response.getVisits()).extracting("visitId")
                .containsExactly(1L, 2L, 3L);
        assertThat(response.getVisits().get(0).isReviewAllowed()).isTrue();
        assertThat(response.getVisits().get(2).isReviewAllowed()).isFalse();
    }

    private Visit buildVisit(Long id, String name, OffsetDateTime arrivedAt) {
        Visit visit = org.mockito.Mockito.mock(Visit.class);
        Place place = org.mockito.Mockito.mock(Place.class);
        given(place.getId()).willReturn(id);
        given(place.getName()).willReturn(name);
        given(visit.getId()).willReturn(id);
        given(visit.getPlace()).willReturn(place);
        given(visit.getArrivedAt()).willReturn(arrivedAt);
        return visit;
    }
}
