package com.matjom.matjom.visit.controller;

import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.common.security.CustomUserDetails;
import com.matjom.matjom.visit.dto.VisitListResponseDTO;
import com.matjom.matjom.visit.service.VisitHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/visits")
@RequiredArgsConstructor
@Tag(name = "Visit", description = "방문 이력 API")
@SecurityRequirement(name = "bearerAuth")
public class VisitController {

    private final VisitHistoryService visitHistoryService;

    @GetMapping("/my")
    @Operation(summary = "내 도착 방문 목록 조회", description = "최근 ARRIVED 방문을 정렬하여 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ApiResponse<VisitListResponseDTO> getMyVisits(
            @AuthenticationPrincipal CustomUserDetails user,
            @Parameter(description = "장소명 검색 키워드", example = "카페", required = false)
            @RequestParam(required = false) String keyword
    ) {
        UUID userId = requireUserId(user);
        VisitListResponseDTO response = visitHistoryService.getMyArrivedVisits(userId, keyword);
        return ApiResponse.ok(response);
    }

    private UUID requireUserId(CustomUserDetails user) {
        UUID userId = Objects.requireNonNull(user, "인증 정보가 필요합니다.").getUserId();
        return Objects.requireNonNull(userId, "사용자 ID가 필요합니다.");
    }
}
