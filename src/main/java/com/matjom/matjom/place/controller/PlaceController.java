package com.matjom.matjom.place.controller;

import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.place.dto.PlaceDetailResponseDTO;
import com.matjom.matjom.place.service.PlaceDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
@Tag(name = "Place", description = "장소 상세 조회 API")
@SecurityRequirement(name = "bearerAuth")
public class PlaceController {

    private final PlaceDetailService placeDetailService;

    @Operation(summary = "장소 상세 조회", description = "장소 기본 정보, 통계, 리뷰를 묶어서 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 파라미터 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "장소를 찾을 수 없음")
    })
    @GetMapping("/{placeId}")
    public ApiResponse<PlaceDetailResponseDTO> getPlaceDetail(
            @Parameter(description = "조회할 장소 ID", required = true)
            @PathVariable @Positive Long placeId,
            @Parameter(description = "최신 리뷰 조회 개수 (생략 시 기본값 15)", example = "10")
            @RequestParam(name = "reviewLimit", required = false) Integer reviewLimit
    ) {
        return ApiResponse.ok(placeDetailService.getPlaceDetail(placeId, reviewLimit));
    }
}
