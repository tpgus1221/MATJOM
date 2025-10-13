package com.matjom.matjom.statistics.controller;

import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.statistics.dto.StatsResponseDTO;
import com.matjom.matjom.statistics.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/places/{placeId}/stats")
@Tag(name = "Statistics", description = "장소 통계 API")
@SecurityRequirement(name = "bearerAuth")
public class StatisticsController {

    private final StatisticsService statisticsService;

    // 클라이언트에 장소 통계를 제공하는 `/stats` 엔드포인트를 처리한다.
    @Operation(summary = "장소 통계 조회", description = "최근 방문/좋아요 집계를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "장소를 찾을 수 없음")
    })
    @GetMapping
    public ApiResponse<StatsResponseDTO> getStatistics(
            @Parameter(description = "통계를 조회할 장소 ID", required = true)
            @PathVariable @Positive Long placeId) {
        StatsResponseDTO response = statisticsService.fetchStats(placeId);
        return ApiResponse.ok(response);
    }
}
