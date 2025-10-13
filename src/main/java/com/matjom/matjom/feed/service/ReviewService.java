package com.matjom.matjom.feed.service;

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
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final VisitEligibilityChecker visitEligibilityChecker;
    private final ProfanityFilter profanityFilter;
    private final UserReadRepository userReadRepository;

    private static final String UNKNOWN_REVIEWER = "알 수 없음";

    @Transactional
    public ReviewResponseDTO createReview(UUID userId, ReviewCreateRequestDTO request) {
        log.info("리뷰 작성 시작: userId={}, placeId={}, visitId={}", userId, request.getPlaceId(), request.getVisitId());

        Long visitId = resolveArrivedVisitId(userId, request.getPlaceId(), request.getVisitId());
        validateReviewCreationWindow(userId, visitId);

        if (reviewRepository.existsByVisitId(visitId)) {
            throw new FeedException(ErrorCode.REVIEW_ALREADY_EXISTS, "이미 리뷰를 작성하셨습니다");
        }

        profanityFilter.validate(request.getText());
        String reviewerName = loadReviewerName(userId);

        Review review = Review.builder()
                .userId(userId)
                .placeId(request.getPlaceId())
                .visitId(visitId)
                .userName(reviewerName)
                .text(request.getText())
                .build();

        Review saved = reviewRepository.save(review);
        log.info("리뷰 작성 완료: reviewId={}, userId={}", saved.getId(), userId);
        return ReviewResponseDTO.of(saved);
    }

    @Transactional
    public ReviewResponseDTO updateReview(UUID userId, UUID reviewId, ReviewUpdateRequestDTO request) {
        log.info("리뷰 수정 요청: userId={}, reviewId={}", userId, reviewId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new FeedException(ErrorCode.REVIEW_NOT_FOUND, "리뷰를 찾을 수 없습니다"));

        if (!review.getUserId().equals(userId)) {
            throw new FeedException(ErrorCode.FORBIDDEN, "자신의 리뷰만 수정할 수 있습니다");
        }
        if (review.isDeleted()) {
            throw new FeedException(ErrorCode.REVIEW_NOT_ALLOWED, "삭제된 리뷰는 수정할 수 없습니다");
        }

        validateReviewUpdateWindow(review);
        profanityFilter.validate(request.getText());

        review.setText(request.getText());
        log.info("리뷰 수정 완료: reviewId={}", reviewId);
        return ReviewResponseDTO.of(review);
    }

    @Transactional
    public void deleteReview(UUID userId, UUID reviewId) {
        log.info("리뷰 삭제 요청: userId={}, reviewId={}", userId, reviewId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new FeedException(ErrorCode.REVIEW_NOT_FOUND, "리뷰를 찾을 수 없습니다"));

        if (!review.getUserId().equals(userId)) {
            throw new FeedException(ErrorCode.FORBIDDEN, "자신의 리뷰만 삭제할 수 있습니다");
        }

        review.markDeleted();
        log.info("리뷰 삭제 완료: reviewId={}", reviewId);
    }

    public List<ReviewResponseDTO> getLatestPlaceReviews(Long placeId, int limit) {
        return reviewRepository.findByPlaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(placeId, PageRequest.of(0, limit)).stream()
                .map(ReviewResponseDTO::of)
                .toList();
    }

    public java.util.List<ReviewResponseDTO> getPlaceReviews(Long placeId) {
        return reviewRepository.findActiveReviewsByPlaceId(placeId).stream()
                .map(ReviewResponseDTO::of)
                .toList();
    }

    public java.util.List<ReviewResponseDTO> getUserReviews(UUID userId) {
        return reviewRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(review -> !review.isDeleted())
                .map(ReviewResponseDTO::of)
                .toList();
    }

    private Long resolveArrivedVisitId(UUID userId, Long placeId, Long requestedVisitId) {
        if (requestedVisitId != null) {
            visitEligibilityChecker.findArrivedAt(userId, requestedVisitId)
                    .orElseThrow(() -> new FeedException(ErrorCode.REVIEW_NOT_ALLOWED, "도착한 방문이 없습니다"));
            return requestedVisitId;
        }

        return visitEligibilityChecker.findLatestArrivedVisitId(userId, placeId)
                .orElseThrow(() -> new FeedException(ErrorCode.REVIEW_NOT_ALLOWED, "도착한 방문이 없습니다"));
    }

    private void validateReviewCreationWindow(UUID userId, Long visitId) {
        OffsetDateTime arrivedAt = visitEligibilityChecker.findArrivedAt(userId, visitId)
                .orElseThrow(() -> new FeedException(ErrorCode.REVIEW_NOT_ALLOWED, "도착한 방문이 없습니다"));
        if (isPast24Hours(arrivedAt)) {
            throw new FeedException(ErrorCode.REVIEW_NOT_ALLOWED, "방문 후 24시간이 지나 리뷰를 작성할 수 없습니다");
        }
    }

    private void validateReviewUpdateWindow(Review review) {
        OffsetDateTime createdAt = review.getCreatedAt();
        if (createdAt == null || isPast24Hours(createdAt)) {
            throw new FeedException(ErrorCode.REVIEW_NOT_ALLOWED, "리뷰 수정 가능 시간이 지났습니다");
        }
    }

    private boolean isPast24Hours(OffsetDateTime baseTime) {
        return baseTime.plusHours(24).isBefore(OffsetDateTime.now());
    }

    private String loadReviewerName(UUID userId) {
        return userReadRepository.findNameById(userId)
                .filter(name -> !name.isBlank())
                .orElse(UNKNOWN_REVIEWER);
    }
}
