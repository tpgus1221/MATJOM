package com.matjom.matjom.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.feed.dto.request.LikeCreateRequestDTO;
import com.matjom.matjom.feed.dto.response.LikeStatusResponseDTO;
import com.matjom.matjom.feed.entity.likes.Like;
import com.matjom.matjom.feed.repository.LikeRepository;
import com.matjom.matjom.visit.service.VisitEligibilityChecker;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private VisitEligibilityChecker visitEligibilityChecker;

    @InjectMocks
    private LikeService likeService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Long PLACE_ID = 1L;
    private static final Long VISIT_ID = 200L;

    @Test
    @DisplayName("도착하지 않았으면 좋아요가 거부된다")
    void createLikeFailsWhenNotArrived() {
        given(visitEligibilityChecker.findArrivedAt(USER_ID, VISIT_ID)).willReturn(Optional.empty());

        LikeCreateRequestDTO request = new LikeCreateRequestDTO(PLACE_ID, VISIT_ID);

        FeedException exception = assertThrows(FeedException.class,
                () -> likeService.createLike(USER_ID, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LIKE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("이미 좋아요가 있으면 중복 예외")
    void createLikeFailsWhenAlreadyExists() {
        given(visitEligibilityChecker.findArrivedAt(USER_ID, VISIT_ID))
                .willReturn(Optional.of(OffsetDateTime.now()));
        given(likeRepository.existsByVisitId(VISIT_ID)).willReturn(true);

        LikeCreateRequestDTO request = new LikeCreateRequestDTO(PLACE_ID, VISIT_ID);

        FeedException exception = assertThrows(FeedException.class,
                () -> likeService.createLike(USER_ID, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LIKE_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("visitId 생략 시 최신 방문 사용")
    void createLikeResolvesLatestVisit() {
        given(visitEligibilityChecker.findLatestArrivedVisitId(USER_ID, PLACE_ID))
                .willReturn(Optional.of(VISIT_ID));
        given(visitEligibilityChecker.findArrivedAt(USER_ID, VISIT_ID))
                .willReturn(Optional.of(OffsetDateTime.now()));
        given(likeRepository.existsByVisitId(VISIT_ID)).willReturn(false);
        Like persisted = Like.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .placeId(PLACE_ID)
                .visitId(VISIT_ID)
                .dateKst(LocalDate.now())
                .build();
        given(likeRepository.save(any(Like.class))).willReturn(persisted);

        LikeStatusResponseDTO response = likeService.createLike(USER_ID, new LikeCreateRequestDTO(PLACE_ID, null));

        assertThat(response.isLiked()).isTrue();
        assertThat(response.getLikeId()).isEqualTo(persisted.getId());
        verify(likeRepository).save(any(Like.class));
    }

    @Test
    @DisplayName("24시간이 지나면 좋아요 생성 거부")
    void createLikeFailsAfter24Hours() {
        given(visitEligibilityChecker.findArrivedAt(USER_ID, VISIT_ID))
                .willReturn(Optional.of(OffsetDateTime.now().minusHours(25)));

        LikeCreateRequestDTO request = new LikeCreateRequestDTO(PLACE_ID, VISIT_ID);

        FeedException exception = assertThrows(FeedException.class,
                () -> likeService.createLike(USER_ID, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LIKE_NOT_ALLOWED);
    }
}
