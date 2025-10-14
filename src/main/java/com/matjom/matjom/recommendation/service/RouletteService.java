package com.matjom.matjom.recommendation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjom.matjom.common.exception.base.IdempotencyException;
import com.matjom.matjom.common.exception.base.RecommendationException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.idempotency.IdempotencyCallback;
import com.matjom.matjom.common.idempotency.IdempotencyResult;
import com.matjom.matjom.common.idempotency.IdempotencyStore;
import com.matjom.matjom.place.repository.PlaceRepository;
import com.matjom.matjom.recommendation.dto.RouletteCandidate;
import com.matjom.matjom.recommendation.dto.RouletteRequest;
import com.matjom.matjom.recommendation.dto.RouletteResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RouletteService {
	// [정책] 기본 반경/후보 상한(예시). “근처 한 끼” UX 기준.
	// 반경/상한의 서비스 기본값들
    private static final double DEFAULT_RADIUS_METERS = 300.0;
    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 500;

	// Redis 멱등 키 prefix (최종 키는 idemp:roulette:{Idempotency-Key})
	private static final String IDEMPOTENCY_PREFIX = "idemp:roulette:";

	// 후보 조회(쿼리) 담당
	private final PlaceRepository placeRepository;

	// 멱등 저장/재생 담당(프로필에 따라 InMemory/Redis 구현)
    private final IdempotencyStore idempotencyStore;

	// 요청을 JSON 바이트로 직렬화하여 해시 생성에 사용
    private final ObjectMapper objectMapper;


    public RouletteResponse recommend(final RouletteRequest request, String idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey"); // 컨트롤러에서 보장하더라도 방어 코드
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;  		 // Redis 저장 키 prefix
		// [1] 요청해시 생성: “같은 요청” 정의를 엄격히 하기 위해 JSON을 안정 직렬화하여 SHA-256으로 해시
        String requestHash = computeRequestHash(request);

		// [2] 멱등 저장소 실행: 동일 key+hash가 있으면 재생, 없으면 콜백 수행
		IdempotencyResult<RouletteResponse> result = idempotencyStore.replayOrRun(
                redisKey,
                requestHash,
                RouletteResponse.class,
                new IdempotencyCallback<RouletteResponse>() {
                    @Override
                    public RouletteResponse execute() {
                        return executeRecommendation(request); // 최초 실행 시 실제 추천 로직 수행
                    }
                }
        );
		// [3] meta.replayed를 응답에 반영(UX: 스낵바 메시지/애니메이션 제어 근거)
        if (result.isReplayed()) {
            return markReplayed(result.getValue());
        }
        return result.getValue();
    }

    private RouletteResponse executeRecommendation(RouletteRequest request) {
        double radius = request.radiusOrDefault(DEFAULT_RADIUS_METERS);
        int limit = Math.min(request.limitOrDefault(DEFAULT_LIMIT), MAX_LIMIT);

        List<RouletteCandidate> candidates = placeRepository.findRouletteCandidates(
                request.getLat(),
                request.getLng(),
                radius,
                request.categoriesOrNull(),
                limit);

        if (candidates.isEmpty()) {
            throw new RecommendationException(ErrorCode.ROULETTE_NO_CANDIDATE);
        }

        int index = selectIndex(candidates.size(), request.getSeed());
        RouletteCandidate chosen = candidates.get(index);

        return new RouletteResponse(
                chosen.placeId(),
                chosen.name(),
                chosen.distanceMeters(),
                chosen.categories(),
                chosen.latitude(),
                chosen.longitude(),
                new RouletteResponse.Meta(candidates.size(), false)
        );
    }

    private RouletteResponse markReplayed(RouletteResponse original) {
        RouletteResponse.Meta meta = original.meta();
        int candidateCount = meta == null ? 0 : meta.candidateCount();
        return new RouletteResponse(
                original.placeId(),
                original.name(),
                original.distanceMeters(),
                original.categories(),
                original.latitude(),
                original.longitude(),
                new RouletteResponse.Meta(candidateCount, true)
        );
    }

    private String computeRequestHash(RouletteRequest request) {
        byte[] jsonBytes = toJsonBytes(request);
        MessageDigest digest = messageDigest();
        byte[] hashed = digest.digest(jsonBytes);
        StringBuilder builder = new StringBuilder(hashed.length * 2);
        for (byte value : hashed) {
            int unsigned = value & 0xFF;
            String hex = Integer.toHexString(unsigned);
            if (hex.length() == 1) {
                builder.append('0');
            }
            builder.append(hex);
        }
        return builder.toString();
    }

    private byte[] toJsonBytes(RouletteRequest request) {
        try {
            return objectMapper.writeValueAsBytes(request);
        } catch (JsonProcessingException ex) {
            throw new IdempotencyException(ErrorCode.INTERNAL_SERVER_ERROR, "요청 직렬화에 실패했습니다.");
        }
    }

    private MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 해시 함수를 사용할 수 없습니다.", ex);
        }
    }

    private int selectIndex(int size, Long seed) {
        if (size <= 1) {
            return 0;
        }
        if (seed == null) {
            return ThreadLocalRandom.current().nextInt(size);
        }
        return new Random(seed).nextInt(size);
    }
}
