package com.matjom.matjom.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.feed.entity.review.Review;
import com.matjom.matjom.feed.repository.ReviewRepository;
import com.matjom.matjom.moderation.report.dto.ReportReviewRequestDTO;
import com.matjom.matjom.moderation.report.entity.ReportReason;
import com.matjom.matjom.moderation.report.entity.ReviewReport;
import com.matjom.matjom.moderation.report.repository.ReviewReportRepository;
import java.util.UUID;

import com.matjom.matjom.moderation.report.service.ReviewModerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewModerationServiceTest {

    @org.mockito.Mock ReviewReportRepository reportRepository;
    @org.mockito.Mock ReviewRepository reviewRepository;

    ReviewModerationService service;

    @BeforeEach
    void setUp() {
        service = new ReviewModerationService(reportRepository, reviewRepository);
    }

    @Test
    void 신고시_중복이면_예외() {
        UUID reviewId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();

        when(reportRepository.existsByReviewIdAndReporterId(reviewId, reporterId)).thenReturn(true);

        assertThatThrownBy(() ->
                service.reportReview(reviewId, reporterId,
                        ReportReviewRequestDTO.builder().reason(ReportReason.SPAM).build()))
                .isInstanceOf(FeedException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS);
    }

    @Test
    void 신고대상_리뷰가_없으면_예외() {
        UUID reviewId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();

        when(reportRepository.existsByReviewIdAndReporterId(reviewId, reporterId)).thenReturn(false);
        when(reviewRepository.existsByIdAndDeletedAtIsNull(reviewId)).thenReturn(false);

        assertThatThrownBy(() ->
                service.reportReview(reviewId, reporterId,
                        ReportReviewRequestDTO.builder().reason(ReportReason.SPAM).build()))
                .isInstanceOf(FeedException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REVIEW_NOT_FOUND);
    }

    @Test
    void 신고가_정상등록되면_건수만_증가반환() {
        UUID reviewId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();

        when(reportRepository.existsByReviewIdAndReporterId(reviewId, reporterId)).thenReturn(false);
        when(reviewRepository.existsByIdAndDeletedAtIsNull(reviewId)).thenReturn(true);
        when(reportRepository.save(any(ReviewReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, ReviewReport.class));
        when(reportRepository.countByReviewId(reviewId)).thenReturn(2L);

        ReportReviewRequestDTO request = ReportReviewRequestDTO.builder()
                .reason(ReportReason.SPAM)
                .description("부적절한 표현")
                .build();

        service.reportReview(reviewId, reporterId, request);

        ArgumentCaptor<ReviewReport> reportCaptor = ArgumentCaptor.forClass(ReviewReport.class);
        verify(reportRepository).save(reportCaptor.capture());

        ReviewReport savedReport = reportCaptor.getValue();
        assertThat(savedReport.getReviewId()).isEqualTo(reviewId);
        assertThat(savedReport.getReporterId()).isEqualTo(reporterId);
        assertThat(savedReport.getReason()).isEqualTo(ReportReason.SPAM);
        assertThat(savedReport.getDescription()).isEqualTo("부적절한 표현");
    }

    @Test
    void 신고가_3회이상이면_리뷰가_자동삭제된다() {
        UUID reviewId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();

        when(reportRepository.existsByReviewIdAndReporterId(reviewId, reporterId)).thenReturn(false);
        when(reviewRepository.existsByIdAndDeletedAtIsNull(reviewId)).thenReturn(true);
        when(reportRepository.save(any(ReviewReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, ReviewReport.class));
        when(reportRepository.countByReviewId(reviewId)).thenReturn(3L);

        Review review = Review.builder()
                .id(reviewId)
                .userId(UUID.randomUUID())
                .placeId(1L)
                .visitId(10L)
                .text("리뷰 내용")
                .build();

        when(reviewRepository.findById(reviewId)).thenReturn(java.util.Optional.of(review));

        service.reportReview(reviewId, reporterId,
                ReportReviewRequestDTO.builder().reason(ReportReason.SPAM).build());

        assertThat(review.isDeleted()).isTrue();
    }
}
