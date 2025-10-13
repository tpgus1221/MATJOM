package com.matjom.matjom.feed.dto.response;

import com.matjom.matjom.feed.entity.review.Review;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewResponseDTO {
    private static final String UNKNOWN_REVIEWER = "알 수 없음";

    private String reviewerName;
    private String text;
    private OffsetDateTime createdAt;

    public static ReviewResponseDTO of(Review review) {
        String reviewerName = review.getUserName();
        if (reviewerName == null || reviewerName.isBlank()) {
            reviewerName = UNKNOWN_REVIEWER;
        }
        return ReviewResponseDTO.builder()
                .reviewerName(reviewerName)
                .text(review.getText())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
