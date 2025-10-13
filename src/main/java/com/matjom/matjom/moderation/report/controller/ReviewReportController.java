package com.matjom.matjom.moderation.report.controller;

import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.common.security.CustomUserDetails;
import com.matjom.matjom.moderation.report.dto.ReportReviewRequestDTO;
import com.matjom.matjom.moderation.report.service.ReviewModerationService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Tag(name = "Moderation - Review Reports", description = "리뷰 신고 API")
@SecurityRequirement(name = "bearerAuth")
public class ReviewReportController {

    private final ReviewModerationService reviewModerationService;

    @Operation(summary = "리뷰 신고", description = "리뷰에 문제가 있는 경우 사유와 함께 신고합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "신고 접수 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "이미 신고했거나 요청이 잘못됨"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "리뷰를 찾을 수 없음")
    })
    @PostMapping("/{reviewId}/reports")
    public ApiResponse<Void> reportReview(@AuthenticationPrincipal CustomUserDetails user,
                                          @Parameter(description = "신고 대상 리뷰 ID", required = true)
                                          @PathVariable UUID reviewId,
                                          @Valid @RequestBody ReportReviewRequestDTO request) {
        UUID userId = requireUserId(user);
        reviewModerationService.reportReview(reviewId, userId, request);
        return ApiResponse.ok();
    }

    private UUID requireUserId(CustomUserDetails user) {
        UUID userId = Objects.requireNonNull(user, "인증 정보가 필요합니다.").getUserId();
        return Objects.requireNonNull(userId, "사용자 ID가 필요합니다.");
    }
}

/*
프런트에서는 신고 버튼을 눌렀을 때 API를 호출하고, 응답의 success 값만 확인해서 토스트(또는 alert)를 띄우면 됩니다.
예시로 React + axios + 토스트 컴포넌트를 쓴다고 가정하면 아래처럼 작성할 수 있습니다.

import axios from 'axios';
import { toast } from '@/components/ui/toast';

async function reportReview(reviewId: string, payload: { reason: string; description?: string }) {
  try {
    const response = await axios.post<ApiResponse<null>>(
      `/api/v1/reviews/${reviewId}/reports`,
      payload,
      { headers: { Authorization: `Bearer ${token}` } }
    );

    if (response.data.success) {
      toast.success('신고가 접수되었습니다.');
    } else {
      toast.error(response.data.error?.message ?? '신고 처리 중 문제가 발생했습니다.');
    }
  } catch (error) {
    toast.error('네트워크 오류로 신고에 실패했습니다.');
  }
}
 */
