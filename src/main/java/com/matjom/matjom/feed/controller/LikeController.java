package com.matjom.matjom.feed.controller;

import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.common.security.CustomUserDetails;
import com.matjom.matjom.feed.dto.request.LikeCreateRequestDTO;
import com.matjom.matjom.feed.dto.response.LikeStatusResponseDTO;
import com.matjom.matjom.feed.service.LikeService;
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
@RequestMapping("/api/v1/likes")
@RequiredArgsConstructor
@Tag(name = "Feed - Likes", description = "좋아요 생성/취소 API")
@SecurityRequirement(name = "bearerAuth")
public class LikeController {

    private final LikeService likeService;

    @Operation(summary = "좋아요 등록", description = "최근 도착 방문을 기준으로 좋아요를 생성합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "좋아요 등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "좋아요 등록 조건 미충족"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 좋아요가 존재")
    })
    @PostMapping
    public ApiResponse<LikeStatusResponseDTO> createLike(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody LikeCreateRequestDTO request
    ) {
        UUID userId = requireUserId(user);
        return ApiResponse.ok(likeService.createLike(userId, request));
    }

    @Operation(summary = "좋아요 취소", description = "등록된 좋아요를 취소합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "좋아요 취소 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "취소 가능 시간이 지났거나 이미 취소됨"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "좋아요를 찾을 수 없음")
    })
    @DeleteMapping("/{likeId}")
    public ApiResponse<LikeStatusResponseDTO> cancelLike(
            @AuthenticationPrincipal CustomUserDetails user,
            @Parameter(description = "취소할 좋아요 ID", required = true)
            @PathVariable UUID likeId
    ) {
        UUID userId = requireUserId(user);
        return ApiResponse.ok(likeService.cancelLike(userId, likeId));
    }

    @Operation(summary = "좋아요 재활성화", description = "취소된 좋아요를 다시 활성화합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "좋아요 재활성화 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "재활성화 조건 미충족"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "좋아요를 찾을 수 없음")
    })
    @PutMapping("/{likeId}")
    public ApiResponse<LikeStatusResponseDTO> reactivateLike(
            @AuthenticationPrincipal CustomUserDetails user,
            @Parameter(description = "재활성화할 좋아요 ID", required = true)
            @PathVariable UUID likeId
    ) {
        UUID userId = requireUserId(user);
        return ApiResponse.ok(likeService.reactivateLike(userId, likeId));
    }

    private UUID requireUserId(CustomUserDetails user) {
        UUID userId = Objects.requireNonNull(user, "인증 정보가 필요합니다.").getUserId();
        return Objects.requireNonNull(userId, "사용자 ID가 필요합니다.");
    }
}
