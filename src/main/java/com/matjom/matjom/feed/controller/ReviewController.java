package com.matjom.matjom.feed.controller;

import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.common.security.CustomUserDetails;
import com.matjom.matjom.feed.dto.request.ReviewCreateRequestDTO;
import com.matjom.matjom.feed.dto.request.ReviewUpdateRequestDTO;
import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.feed.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Tag(name = "Feed - Reviews", description = "리뷰 작성과 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class ReviewController {
    private final ReviewService reviewService;

    @Operation(summary = "리뷰 작성", description = "도착한 방문 정보를 기반으로 리뷰를 신규 작성합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "리뷰 작성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "리뷰 작성 조건 미충족"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "대상 방문 또는 사용자 없음")
    })
    @PostMapping
    public ApiResponse<ReviewResponseDTO> createReview(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ReviewCreateRequestDTO request
    ) {
        UUID userId = requireUserId(user);
        return ApiResponse.ok(reviewService.createReview(userId, request));
    }

    @Operation(summary = "리뷰 수정", description = "작성한 리뷰를 24시간 이내에 수정합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "리뷰 수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "수정 가능 시간이 지났거나 요청이 잘못됨"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "본인 리뷰가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "리뷰를 찾을 수 없음")
    })
    @PutMapping("/{reviewId}")
    public ApiResponse<ReviewResponseDTO> updateReview(
            @AuthenticationPrincipal CustomUserDetails user,
            @Parameter(description = "수정할 리뷰 ID", required = true)
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewUpdateRequestDTO request
    ) {
        UUID userId = requireUserId(user);
        return ApiResponse.ok(reviewService.updateReview(userId, reviewId, request));
    }

    @Operation(summary = "리뷰 삭제", description = "작성한 리뷰를 삭제(소프트 삭제)합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "리뷰 삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "본인 리뷰가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "리뷰를 찾을 수 없음")
    })
    @DeleteMapping("/{reviewId}")
    public ApiResponse<Void> deleteReview(
            @AuthenticationPrincipal CustomUserDetails user,
            @Parameter(description = "삭제할 리뷰 ID", required = true)
            @PathVariable UUID reviewId
    ) {
        UUID userId = requireUserId(user);
        reviewService.deleteReview(userId, reviewId);
        return ApiResponse.ok();
    }

    private UUID requireUserId(CustomUserDetails user) {
        UUID userId = Objects.requireNonNull(user, "인증 정보가 필요합니다.").getUserId();
        return Objects.requireNonNull(userId, "사용자 ID가 필요합니다.");
    }
}
