package com.matjom.matjom.recommendation.api;

import com.matjom.matjom.common.exception.base.RecommendationException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.recommendation.dto.RouletteRequest;
import com.matjom.matjom.recommendation.dto.RouletteResponse;
import com.matjom.matjom.recommendation.service.RouletteService;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/recommendations")
@Validated
@RequiredArgsConstructor
public class RouletteController {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 200;
	// [비즈니스] 멱등키는 **60초 캐시**의 키가 되며, 동일 키+동일 요청해시일 때
	// 저장된 응답을 “재생(replay)”한다. DDOS/중복탭 방지 및 “사용자가 같은 요청을 의도적으로 반복”할 때
	// 동일 결과를 보장하기 위함. 길이 상한으로 헤더 오용/공격을 억제.

    private final RouletteService rouletteService;

	// [계약] “룰렛 추천” 엔드포인트.
	// - 헤더 `Idempotency-Key` 필수: 60s 내 동일 요청이면 같은 응답을 재생.
	// - 본문 `seed`가 있으면 후보 선택이 **재현 가능**(동 seed → 동 후보).
	// - 반경/카테고리/limit에 따라 후보풀을 구성하고, **균등 확률**로 1개를 선택한다.
    @PostMapping("/roulette")
    public ApiResponse<RouletteResponse> postRoulette(@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                                      @Valid @RequestBody RouletteRequest request) {
        String sanitizedKey = validateIdempotencyKey(idempotencyKey); // 빈 헤더/200자 초과 방지

		// [흐름] 서비스에서
		// (1) 요청 JSON을 안정 직렬화 → SHA-256으로 request-hash 생성
		// (2) IdempotencyStore.replayOrRun(key, hash, ...)
		//     - 동일 key+hash가 60s 내 존재 -> 저장 응답 재생(replayed=true)
		//     - 없으면 후보 조회 -> 균등 선택 -> 응답 저장(replayed=false)
		RouletteResponse response = rouletteService.recommend(request, sanitizedKey);// 멱등 키와 함께 서비스 호출
        return ApiResponse.ok(response); // 공통 응답 규약 적용
    }

    private String validateIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) { // 헤더 누락 → IDEMPOTENCY_KEY_REQUIRED

			// [계약 위반] 멱등 보장은 필수 정책: 네트워크 재시도/중복 탭에서 “중복 추천”을 방지해야 함.
			throw new RecommendationException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > IDEMPOTENCY_KEY_MAX_LENGTH) { // 200자 초과 방지
			// [보안/운영] 과도한 헤더 길이로 인한 저장소/로그 오염 방지
            throw new RecommendationException(ErrorCode.INVALID_REQUEST_PARAM, "Idempotency-Key 길이는 200자를 초과할 수 없습니다.");
        }
        return trimmed;
    }
}
