package com.matjom.matjom.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.feed.dto.request.ReviewCreateRequestDTO;
import com.matjom.matjom.feed.dto.request.ReviewUpdateRequestDTO;
import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.feed.entity.review.Review;
import com.matjom.matjom.feed.repository.ReviewRepository;
import com.matjom.matjom.feed.repository.UserReadRepository;
import com.matjom.matjom.moderation.profanity.ProfanityFilter;
import com.matjom.matjom.visit.service.VisitEligibilityChecker;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private VisitEligibilityChecker visitEligibilityChecker;

    @Mock
    private ProfanityFilter profanityFilter;

    @Mock
    private UserReadRepository userReadRepository;

    @InjectMocks
    private ReviewService reviewService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Long PLACE_ID = 1L;
    private static final Long VISIT_ID = 100L;

    @Test
    @DisplayName("도착하지 않았으면 리뷰 작성이 거부된다")
    void createReviewFailsWhenNotArrived() {
        given(visitEligibilityChecker.findArrivedAt(USER_ID, VISIT_ID)).willReturn(Optional.empty());

        ReviewCreateRequestDTO request = new ReviewCreateRequestDTO(PLACE_ID, VISIT_ID, "맛있어요");

        FeedException exception = assertThrows(FeedException.class,
                () -> reviewService.createReview(USER_ID, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REVIEW_NOT_ALLOWED);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 리뷰가 있을 때 중복 작성이 거부된다")
    void createReviewFailsWhenAlreadyWritten() {
        given(visitEligibilityChecker.findArrivedAt(USER_ID, VISIT_ID))
                .willReturn(Optional.of(OffsetDateTime.now()));
        given(reviewRepository.existsByVisitId(VISIT_ID)).willReturn(true);

        ReviewCreateRequestDTO request = new ReviewCreateRequestDTO(PLACE_ID, VISIT_ID, "맛있어요");

        FeedException exception = assertThrows(FeedException.class,
                () -> reviewService.createReview(USER_ID, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("visitId 없이 요청하면 최신 ARRIVED 방문을 사용한다")
    void createReviewResolvesLatestVisitWhenNotProvided() {
        given(visitEligibilityChecker.findLatestArrivedVisitId(USER_ID, PLACE_ID))
                .willReturn(Optional.of(VISIT_ID));
        given(visitEligibilityChecker.findArrivedAt(USER_ID, VISIT_ID))
                .willReturn(Optional.of(OffsetDateTime.now()));
        given(reviewRepository.existsByVisitId(VISIT_ID)).willReturn(false);
        given(userReadRepository.findNameById(USER_ID)).willReturn(Optional.of("홍길동"));
        Review persisted = Review.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .placeId(PLACE_ID)
                .visitId(VISIT_ID)
                .userName("홍길동")
                .text("맛있어요")
                .build();
        given(reviewRepository.save(any(Review.class))).willReturn(persisted);

        ReviewCreateRequestDTO request = new ReviewCreateRequestDTO(PLACE_ID, null, "맛있어요");

        ReviewResponseDTO response = reviewService.createReview(USER_ID, request);

        assertThat(response.getReviewerName()).isEqualTo("홍길동");
        assertThat(response.getText()).isEqualTo("맛있어요");
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    @DisplayName("도착 후 24시간이 지나면 리뷰 작성이 거부된다")
    void createReviewFailsAfter24Hours() {
        given(visitEligibilityChecker.findArrivedAt(USER_ID, VISIT_ID))
                .willReturn(Optional.of(OffsetDateTime.now().minusHours(25)));

        ReviewCreateRequestDTO request = new ReviewCreateRequestDTO(PLACE_ID, VISIT_ID, "늦었어요");

        FeedException exception = assertThrows(FeedException.class,
                () -> reviewService.createReview(USER_ID, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REVIEW_NOT_ALLOWED);
    }

    @Test
    @DisplayName("리뷰 수정은 작성 후 24시간 이내에만 가능하다")
    void updateReviewFailsAfter24Hours() {
        Review review = Review.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .placeId(PLACE_ID)
                .visitId(VISIT_ID)
                .userName("홍길동")
                .text("초기")
                .build();
        ReflectionTestUtils.setField(review, "createdAt", OffsetDateTime.now().minusHours(25));
        given(reviewRepository.findById(review.getId())).willReturn(Optional.of(review));

        FeedException exception = assertThrows(FeedException.class,
                () -> reviewService.updateReview(USER_ID, review.getId(), new ReviewUpdateRequestDTO("수정")));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REVIEW_NOT_ALLOWED);
    }

    @Test
    @DisplayName("리뷰 수정은 24시간 내에는 정상 처리된다")
    void updateReviewSucceedsWithin24Hours() {
        Review review = Review.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .placeId(PLACE_ID)
                .visitId(VISIT_ID)
                .userName("홍길동")
                .text("초기")
                .build();
        ReflectionTestUtils.setField(review, "createdAt", OffsetDateTime.now().minusHours(2));
        given(reviewRepository.findById(review.getId())).willReturn(Optional.of(review));

        ReviewResponseDTO response = reviewService.updateReview(USER_ID, review.getId(), new ReviewUpdateRequestDTO("수정"));

        assertThat(response.getText()).isEqualTo("수정");
        assertThat(review.getText()).isEqualTo("수정");
    }
}
