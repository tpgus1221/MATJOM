package com.matjom.matjom.feed.service;

import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.feed.dto.request.LikeCreateRequestDTO;
import com.matjom.matjom.feed.dto.response.LikeStatusResponseDTO;
import com.matjom.matjom.feed.entity.likes.Like;
import com.matjom.matjom.feed.repository.LikeRepository;
import com.matjom.matjom.visit.service.VisitEligibilityChecker;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LikeService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final LikeRepository likeRepository;
    private final VisitEligibilityChecker visitEligibilityChecker;

    @Transactional
    public LikeStatusResponseDTO createLike(UUID userId, LikeCreateRequestDTO request) {
        log.info("좋아요 등록 요청: userId={}, placeId={}, visitId={}", userId, request.getPlaceId(), request.getVisitId());

        Long visitId = resolveArrivedVisitIdForLike(userId, request.getPlaceId(), request.getVisitId());

        if (likeRepository.existsByVisitId(visitId)) {
            throw new FeedException(ErrorCode.LIKE_ALREADY_EXISTS, "이미 좋아요를 누르셨습니다");
        }

        Like like = Like.builder()
                .userId(userId)
                .placeId(request.getPlaceId())
                .visitId(visitId)
                .dateKst(LocalDate.now(KST))
                .build();

        Like saved = likeRepository.save(like);
        log.info("좋아요 등록 완료: likeId={}", saved.getId());
        return LikeStatusResponseDTO.builder()
                .liked(true)
                .likeId(saved.getId())
                .build();
    }

    @Transactional
    public LikeStatusResponseDTO cancelLike(UUID userId, UUID likeId) {
        Like like = likeRepository.findByIdAndUserId(likeId, userId)
                .orElseThrow(() -> new FeedException(ErrorCode.LIKE_NOT_ALLOWED, "좋아요를 찾을 수 없습니다."));

        ensureWithinWindow(userId, like.getVisitId());

        if (!like.isActive()) {
            throw new FeedException(ErrorCode.LIKE_NOT_ALLOWED, "이미 취소된 좋아요입니다.");
        }

        like.cancel(OffsetDateTime.now());
        log.info("좋아요 취소 완료: likeId={}", likeId);
        return LikeStatusResponseDTO.builder()
                .liked(false)
                .likeId(likeId)
                .build();
    }

    @Transactional
    public LikeStatusResponseDTO reactivateLike(UUID userId, UUID likeId) {
        Like like = likeRepository.findByIdAndUserId(likeId, userId)
                .orElseThrow(() -> new FeedException(ErrorCode.LIKE_NOT_ALLOWED, "좋아요를 찾을 수 없습니다."));

        ensureWithinWindow(userId, like.getVisitId());

        if (like.isActive()) {
            throw new FeedException(ErrorCode.LIKE_ALREADY_EXISTS, "이미 활성화된 좋아요입니다.");
        }

        like.reactivate();
        log.info("좋아요 재활성화 완료: likeId={}", likeId);
        return LikeStatusResponseDTO.builder()
                .liked(true)
                .likeId(likeId)
                .build();
    }

    private Long resolveArrivedVisitIdForLike(UUID userId, Long placeId, Long requestedVisitId) {
        if (requestedVisitId != null) {
            ensureWithinWindow(userId, requestedVisitId);
            return requestedVisitId;
        }

        Long visitId = visitEligibilityChecker.findLatestArrivedVisitId(userId, placeId)
                .orElseThrow(() -> new FeedException(ErrorCode.LIKE_NOT_ALLOWED, "도착한 방문이 없습니다"));
        ensureWithinWindow(userId, visitId);
        return visitId;
    }

    private void ensureWithinWindow(UUID userId, Long visitId) {
        OffsetDateTime arrivedAt = visitEligibilityChecker.findArrivedAt(userId, visitId)
                .orElseThrow(() -> new FeedException(ErrorCode.LIKE_NOT_ALLOWED, "도착한 방문이 없습니다"));
        if (arrivedAt.plusHours(24).isBefore(OffsetDateTime.now())) {
            throw new FeedException(ErrorCode.LIKE_NOT_ALLOWED, "방문 후 24시간이 지나 좋아요를 변경할 수 없습니다");
        }
    }
}
