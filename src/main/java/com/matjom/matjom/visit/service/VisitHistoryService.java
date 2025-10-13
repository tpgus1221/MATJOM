package com.matjom.matjom.visit.service;

import com.matjom.matjom.visit.dto.VisitCardResponseDTO;
import com.matjom.matjom.visit.dto.VisitListResponseDTO;
import com.matjom.matjom.feed.entity.likes.Like;
import com.matjom.matjom.feed.entity.review.Review;
import com.matjom.matjom.feed.repository.LikeRepository;
import com.matjom.matjom.feed.repository.ReviewRepository;
import com.matjom.matjom.visit.repository.VisitReadRepository;
import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitState;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VisitHistoryService {

    private static final VisitState ARRIVED = VisitState.ARRIVED;

    private final VisitReadRepository visitReadRepository;
    private final ReviewRepository reviewRepository;
    private final LikeRepository likeRepository;

    public VisitListResponseDTO getMyArrivedVisits(UUID userId, String keyword) {
        String trimmedKeyword = normalizeKeyword(keyword);

        List<Visit> visits = visitReadRepository.findArrivedVisits(userId, ARRIVED, trimmedKeyword);
        if (visits.isEmpty()) {
            return VisitListResponseDTO.builder()
                    .visits(List.of())
                    .build();
        }

        Map<Long, Review> reviewsByVisitId = loadActiveReviews(visits);
        Map<Long, Like> likesByVisitId = loadExistingLikes(visits);

        List<VisitCardResponseDTO> cards = new ArrayList<>(visits.size());
        for (Visit visit : visits) {
            cards.add(toDto(visit,
                    reviewsByVisitId.get(visit.getId()),
                    likesByVisitId.get(visit.getId())));
        }

        List<VisitCardResponseDTO> ordered = reorder(cards);
        return VisitListResponseDTO.builder()
                .visits(List.copyOf(ordered))
                .build();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String value = keyword.trim();
        return value.isEmpty() ? null : value;
    }

    private List<VisitCardResponseDTO> reorder(List<VisitCardResponseDTO> cards) {
        List<VisitCardResponseDTO> pending = new ArrayList<>();
        List<VisitCardResponseDTO> completed = new ArrayList<>();

        for (VisitCardResponseDTO card : cards) {
            boolean actionable = !card.isReviewed() && card.isReviewAllowed();
            if (actionable) {
                pending.add(card);
            } else {
                completed.add(card);
            }
        }

        pending.sort((left, right) -> requireArrivedAt(left).compareTo(requireArrivedAt(right)));
        completed.sort((left, right) -> requireArrivedAt(right).compareTo(requireArrivedAt(left)));

        List<VisitCardResponseDTO> combined = new ArrayList<>(pending.size() + completed.size());
        combined.addAll(pending);
        combined.addAll(completed);
        return combined;
    }

    private OffsetDateTime requireArrivedAt(VisitCardResponseDTO card) {
        OffsetDateTime arrivedAt = card.getArrivedAt();
        if (arrivedAt == null) {
            throw new IllegalStateException("ARRIVED 방문은 arrivedAt이 비어있을 수 없습니다. visitId=" + card.getVisitId());
        }
        return arrivedAt;
    }

    private Map<Long, Review> loadActiveReviews(List<Visit> visits) {
        List<Long> visitIds = extractVisitIds(visits);
        return reviewRepository.findByVisitIdInAndDeletedAtIsNull(visitIds).stream()
                .collect(Collectors.toMap(Review::getVisitId, Function.identity()));
    }

    private Map<Long, Like> loadExistingLikes(List<Visit> visits) {
        List<Long> visitIds = extractVisitIds(visits);
        return likeRepository.findByVisitIdInAndDeletedAtIsNull(visitIds).stream()
                .collect(Collectors.toMap(Like::getVisitId, Function.identity()));
    }

    private List<Long> extractVisitIds(List<Visit> visits) {
        return visits.stream()
                .map(Visit::getId)
                .toList();
    }

    private VisitCardResponseDTO toDto(Visit visit, Review review, Like like) {
        UUID reviewId = review == null ? null : review.getId();
        UUID likeId = like == null ? null : like.getId();
        boolean reviewed = review != null;
        boolean liked = like != null && like.isActive();
        OffsetDateTime arrivedAt = visit.getArrivedAt();
        boolean reviewAllowed = !isPast24Hours(arrivedAt);
        boolean likeAllowed = reviewAllowed;
        OffsetDateTime reviewCreatedAt = review == null ? null : review.getCreatedAt();
        boolean reviewEditable = review != null && !isPast24Hours(reviewCreatedAt);
        boolean reviewDeletable = review != null;
        return VisitCardResponseDTO.builder()
                .visitId(visit.getId())
                .placeId(visit.getPlace().getId())
                .placeName(visit.getPlace().getName())
                .arrivedAt(arrivedAt)
                .reviewed(reviewed)
                .reviewId(reviewId)
                .likeId(likeId)
                .liked(liked)
                .reviewAllowed(reviewAllowed)
                .likeAllowed(likeAllowed)
                .reviewEditable(reviewEditable)
                .reviewDeletable(reviewDeletable)
                .build();
    }

    private boolean isPast24Hours(OffsetDateTime base) {
        if (base == null) {
            return true;
        }
        return base.plusHours(24).isBefore(OffsetDateTime.now());
    }
}
