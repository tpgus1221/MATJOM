package com.matjom.matjom.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.feed.dto.request.LikeCreateRequestDTO;
import com.matjom.matjom.feed.dto.response.LikeStatusResponseDTO;
import com.matjom.matjom.feed.entity.likes.Like;
import com.matjom.matjom.feed.entity.likes.LikeStatus;
import com.matjom.matjom.feed.repository.LikeRepository;
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
class LikeServiceIntegrationTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private LikeRepository likeRepository;

    @MockBean
    private VisitEligibilityChecker visitEligibilityChecker;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Long PLACE_ID = 2L;
    private static final Long VISIT_ID = 20L;

    @Test
    @DisplayName("ARRIVED 조건 통과 시 좋아요 저장")
    void createLikePersistsWhenEligible() {
        given(visitEligibilityChecker.findArrivedAt(USER_ID, VISIT_ID))
                .willReturn(Optional.of(OffsetDateTime.now()));
        LikeCreateRequestDTO request = new LikeCreateRequestDTO(PLACE_ID, VISIT_ID);

        LikeStatusResponseDTO response = likeService.createLike(USER_ID, request);

        Like saved = likeRepository.findByVisitId(VISIT_ID).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LikeStatus.ACTIVE);
        assertThat(response.isLiked()).isTrue();
        assertThat(response.getLikeId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("도착하지 않은 방문이면 LIKE_NOT_ALLOWED")
    void createLikeFailsWhenVisitMissing() {
        given(visitEligibilityChecker.findArrivedAt(USER_ID, VISIT_ID)).willReturn(Optional.empty());
        LikeCreateRequestDTO request = new LikeCreateRequestDTO(PLACE_ID, VISIT_ID);

        FeedException exception = assertThrows(FeedException.class,
                () -> likeService.createLike(USER_ID, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LIKE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("24시간이 지나면 좋아요 생성 제한")
    void createLikeFailsAfter24Hours() {
        given(visitEligibilityChecker.findArrivedAt(USER_ID, VISIT_ID))
                .willReturn(Optional.of(OffsetDateTime.now().minusHours(30)));
        LikeCreateRequestDTO request = new LikeCreateRequestDTO(PLACE_ID, VISIT_ID);

        FeedException exception = assertThrows(FeedException.class,
                () -> likeService.createLike(USER_ID, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LIKE_NOT_ALLOWED);
    }
}
