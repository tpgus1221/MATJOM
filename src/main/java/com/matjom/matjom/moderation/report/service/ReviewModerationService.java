package com.matjom.matjom.moderation.report.service;

import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.feed.repository.ReviewRepository;
import com.matjom.matjom.moderation.report.dto.ReportReviewRequestDTO;
import com.matjom.matjom.moderation.report.entity.ReviewReport;
import com.matjom.matjom.moderation.report.repository.ReviewReportRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReviewModerationService {
    private final ReviewReportRepository reviewReportRepository;
    private final ReviewRepository reviewRepository;

    @Transactional
    public void reportReview(UUID reviewId,
                             UUID reporterId,
                             ReportReviewRequestDTO request) {
        if (reviewReportRepository.existsByReviewIdAndReporterId(reviewId, reporterId)) {
            throw new FeedException(ErrorCode.REVIEW_ALREADY_EXISTS, "이미 신고한 리뷰입니다.");
        }
        if (!reviewRepository.existsByIdAndDeletedAtIsNull(reviewId)) {
            throw new FeedException(ErrorCode.REVIEW_NOT_FOUND, "리뷰를 찾을 수 없습니다.");
        }

        ReviewReport saved = reviewReportRepository.save(
                ReviewReport.builder()
                        .reviewId(reviewId)
                        .reporterId(reporterId)
                        .reason(request.getReason())
                        .description(request.getDescription())
                        .build()
        );

        long reportCount = reviewReportRepository.countByReviewId(reviewId);

        if (reportCount >= 3) {
            reviewRepository.findById(reviewId)
                    .filter(review -> !review.isDeleted())
                    .ifPresent(review -> {
                        review.markDeleted();
                        log.info("리뷰 자동 삭제 처리: reviewId={}, reportCount={}", reviewId, reportCount);
                    });
        }

        log.info("리뷰 신고 기록 생성: reviewId={}, reporterId={}, reportId={}, reportCount={}",
                reviewId, reporterId, saved.getId(), reportCount);
    }
}
