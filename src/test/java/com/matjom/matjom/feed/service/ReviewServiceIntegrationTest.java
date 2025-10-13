package com.matjom.matjom.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.feed.dto.request.ReviewCreateRequestDTO;
import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.feed.entity.review.Review;
import com.matjom.matjom.feed.repository.ReviewRepository;
import com.matjom.matjom.feed.repository.UserReadRepository;
import com.matjom.matjom.visit.service.VisitEligibilityChecker;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReviewServiceIntegrationTest {

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ReviewRepository reviewRepository;

    @MockBean
    private VisitEligibilityChecker visitEligibilityChecker;

    @MockBean
    private UserReadRepository userReadRepository;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Long PLACE_ID = 1L;
    private static final Long VISIT_ID = 10L;

    @Test
    @DisplayName("ARRIVED 조건을 통과하면 리뷰가 저장된다")
    void createReviewPersistsWhenEligible() {
        given(visitEligibilityChecker.findArrivedAt(USER_ID, VISIT_ID))
                .willReturn(Optional.of(OffsetDateTime.now()));
        given(userReadRepository.findNameById(USER_ID)).willReturn(Optional.of("홍길동"));
        ReviewCreateRequestDTO request = new ReviewCreateRequestDTO(PLACE_ID, VISIT_ID, "맛있어요");

        ReviewResponseDTO response = reviewService.createReview(USER_ID, request);

        Optional<Review> saved = reviewRepository.findByVisitId(VISIT_ID);
        assertThat(saved).isPresent();
        assertThat(saved.get().getText()).isEqualTo("맛있어요");
        assertThat(saved.get().getUserName()).isEqualTo("홍길동");
        assertThat(response.getReviewerName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("도착하지 않은 방문이면 REVIEW_NOT_ALLOWED")
    void createReviewFailsWhenVisitMissing() {
        given(visitEligibilityChecker.findArrivedAt(USER_ID, VISIT_ID)).willReturn(Optional.empty());
        given(userReadRepository.findNameById(USER_ID)).willReturn(Optional.of("홍길동"));
        ReviewCreateRequestDTO request = new ReviewCreateRequestDTO(PLACE_ID, VISIT_ID, "맛없어요");

        FeedException exception = assertThrows(FeedException.class,
                () -> reviewService.createReview(USER_ID, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REVIEW_NOT_ALLOWED);
    }

    @Test
    @DisplayName("24시간이 지나면 리뷰 작성이 제한된다")
    void createReviewFailsAfter24Hours() {
        given(visitEligibilityChecker.findArrivedAt(USER_ID, VISIT_ID))
                .willReturn(Optional.of(OffsetDateTime.now().minusHours(30)));
        given(userReadRepository.findNameById(USER_ID)).willReturn(Optional.of("홍길동"));
        ReviewCreateRequestDTO request = new ReviewCreateRequestDTO(PLACE_ID, VISIT_ID, "늦은 리뷰");

        FeedException exception = assertThrows(FeedException.class,
                () -> reviewService.createReview(USER_ID, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REVIEW_NOT_ALLOWED);
    }
}
