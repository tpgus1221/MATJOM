package com.matjom.matjom.place.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjom.matjom.place.dto.PlaceSearchCursor;
import com.matjom.matjom.place.dto.PlaceSearchRequest;
import com.matjom.matjom.place.dto.PlaceSearchResponse;
import com.matjom.matjom.place.dto.PlaceSearchResponse.PlaceSummary;
import com.matjom.matjom.place.repository.PlaceRepository;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlaceSearchService {
	// [정책] 반경 미지정 시 “근처 탐색” 경험을 보장하기 위한 기본값.
	// UX 상, 300m는 도보 5~7분 내 후보를 적정하게 제공(밀집 지역 기준)한다는 가정.
    private static final double DEFAULT_RADIUS_METERS = 300.0;

	// [정책] 페이지 기본 크기 20: 모바일 목록 1~2뷰포트에 맞는 양. 캐시/커서 기준 안정화.
    private static final int DEFAULT_PAGE_SIZE = 20;

	// [정책/운영] 서버 절대 상한. 악의적/과도 요청으로 인한 DB 부하 방지.
	// 500건 상한. pageSize 계산과 초과 시 meta.reason="too_many_results" 안내에 사용됩니다.
    private static final int MAX_RESULTS_PER_SEARCH = 500;

	// [조회 전략] “+1” 패턴으로 nextCursor 존재 여부/상한 초과를 감지하기 위한 이론상 값.
	// (현재는 직접 pageSize+1 계산 사용. 상수를 유지하는 이유: 정책 가독성)
	private static final int MAX_FETCH_LIMIT = MAX_RESULTS_PER_SEARCH + 1;

	// [캐시 정책] 목록은 60초 캐시. 근처 식당 정보는 1분 이내 변해도 UX 영향이 작고,
	// 반대로 조회는 잦기 때문에 TTL 60s로 “부하 절감 vs 신선도” 균형.
	private static final Duration CACHE_TTL = Duration.ofSeconds(60);

	// [UX 정책] 결과가 너무 적을 때(예: <20) 확장 가이드(반경↑/필터 완화) 노출 기준.
	private static final int LOW_RESULTS_SUGGEST_THRESHOLD = 20;

    private final PlaceRepository placeRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PlaceSearchResponse search(PlaceSearchRequest request) {
		// [전제] 컨트롤러/DTO @Valid 통과 → 좌표 범위·size 상한 등 1차 검증 완료 상태.

		// [핵심 입력] 기준 좌표(lat/lng)와 검색 반경(m). 반경 미입력 시 300m.
		double lat = request.getLat(); //컨트롤러 검증을 통과한 위도 → PostGIS 쿼리 기준점
        double lng = request.getLng(); //경도 역시 그대로 거리 계산에 활용
        double radius = request.radiusOrDefault(DEFAULT_RADIUS_METERS); // 입력이 없으면 300m 기본값 적용

		// [페이지 정책] 클라 요청이 크더라도 서버는 항상 500 상한으로 제어.
		int requestedSize = request.sizeOrDefault(DEFAULT_PAGE_SIZE); // 클라이언트 요청 페이지 크기(없으면 20)
        int pageSize = Math.min(requestedSize, MAX_RESULTS_PER_SEARCH); //500개 상한으로 서버 부담을 제어

		// [커서 정책] "distance:lastId" → 거리 ASC, 동거리 시 ID ASC 안정 정렬을 “무상태”로 이어가기 위한 절대 기준.\
		PlaceSearchCursor cursorToken = request.parseCursor().orElse(null); // 커서 문자열을 DTO가 파싱해 Optional로 전달

		// [캐시 키 설계] 동일 좌표·반경·size·cursor·filters 조합을 동일 결과로 취급.
		// - 좌표 6자리 소수(≈0.11m)로 포맷 → 지나치게 미세한 값 차이로 캐시 미스 방지(그래도 좌표 흔들림은 존재).
		// - 개선 여지: geohash(30m 버킷)나 반경 버킷팅으로 캐시 히트율 상승 가능.
        String cacheKey = buildCacheKey(lat, lng, radius, requestedSize, request.getCursor(), request.getFilters()); // 요청 파라미터 전체를 포함한 캐시 키 → 결과 정합성 유지

		// [캐시 조회] 실패 시(파싱 오류 등) 캐시를 조용히 무시하고 DB 재조회 → 가용성 우선.
		ValueOperations<String, String> ops = redisTemplate.opsForValue(); // Redis 문자열 연산 핸들 (get/set)

        PlaceSearchResponse cached = readCache(ops, cacheKey); // 캐시에서 직전 응답을 조회, 손상 시 삭제
        if (cached != null) {
            return cached; // [핫패스] 동일 조건 재요청은 그대로 반환(낙관적 캐시)
        }

		// [조회 상한/커서 판단] 항상 +1로 가져와서:
		//  - (size < fetched) → nextCursor 존재
		//  - (size == 500 && fetched > 500) → 상한 초과(too_many_results) 안내
        int fetchLimit = pageSize == MAX_RESULTS_PER_SEARCH
                ? MAX_RESULTS_PER_SEARCH + 1 // 500 요청 시 501번째까지 조회해 상한 초과 여부 확인
                : pageSize + 1; // 일반 페이지도 +1로 조회해 다음 커서 존재 여부 판단

		// [레포 호출] PostGIS Native 쿼리:
		//  - ST_DWithin(…, :radius, true)로 반경 필터 (geography, meter 단위)
		//  - ST_Distance(…, true)로 거리(m) 산출
		//  - ORDER BY distance ASC, place_id ASC
		//  - 커서: distance>cursor.distance OR (distance=cursor.distance AND id>cursor.id)
		//  - LIMIT fetchLimit
        List<PlaceSummary> fetchedSummaries = placeRepository.search(lat, lng, radius, fetchLimit, cursorToken, request.getFilters()); // PostGIS Native SQL 실행 (filters는 후속 과제)

		// [상한 초과 판단] 501건 이상이면 'too_many_results' 메타로 UX 가이드(반경↓/필터↑) 제공.
		boolean exceedsMaxResults = fetchedSummaries.size() > MAX_RESULTS_PER_SEARCH;

		// [nextCursor 생성] pageSize 경계 값의 (distance, id)로 토큰화.
        String nextCursor = buildNextCursor(fetchedSummaries, pageSize);

		// [실제 페이지 절단] 프런트에 전달되는 본문은 size에 정확히 맞춘다.
        List<PlaceSummary> pageSummaries = trimToPage(fetchedSummaries, pageSize);

		// [UX 메타] 결과 밀도에 따른 안내. (i18n 필요 시 키 전환 가능)
        PlaceSearchResponse.Meta meta;
        if (exceedsMaxResults) {
            meta = new PlaceSearchResponse.Meta("too_many_results", "검색 반경을 줄이거나 필터를 추가해 주세요."); // 상한 초과 안내 문구
        } else if (pageSummaries.size() < LOW_RESULTS_SUGGEST_THRESHOLD) {
            meta = new PlaceSearchResponse.Meta("low_results", "검색 결과가 적습니다. 반경을 늘리거나 필터를 완화해 보세요."); // 결과가 적을 때 UX 가이드 제공
        } else {
            meta = null; // 특별 안내가 필요 없는 경우 메타 생략
        }
        PlaceSearchResponse response = new PlaceSearchResponse(pageSummaries, nextCursor, meta); // 본문/커서/메타를 묶어 응답 객체 생성

		// [캐시 적재] 조회 부하 완화용 낙관적 캐시. TTL 60s. 캐시 실패는 무시(가용성 우선).
        writeCache(ops, cacheKey, response); // 동일 조건 재요청 대비 60초 캐싱
        return response; // 최종 응답 반환
    }

	// [캐시 읽기] JSON 직렬화/역직렬화. 불완전 데이터(파싱 실패) 감지 시 캐시 제거로 자기치유.
    private PlaceSearchResponse readCache(ValueOperations<String, String> ops, String cacheKey) {
        String cachedJson = ops.get(cacheKey);   // 1) Redis에서 문자열(JSON) 조회
        if (!StringUtils.hasText(cachedJson)) {  // 2) null/빈문자면 캐시 미스
            return null;
        }
        try {
            return objectMapper.readValue(cachedJson, PlaceSearchResponse.class);   // 3) JSON→DTO
        } catch (JsonProcessingException e) {
            redisTemplate.delete(cacheKey);  // 캐시 데이터가 손상된 경우 삭제하고 캐시 미스 처리
            return null;  // 그리고 캐시 미스 처리
        }
    }

    private void writeCache(ValueOperations<String, String> ops, String cacheKey, PlaceSearchResponse response) {
        try {
            ops.set(cacheKey, objectMapper.writeValueAsString(response), CACHE_TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("검색 결과 직렬화에 실패했습니다.", e);
        }
    }

    private String buildCacheKey(double lat, double lng, double radius, int size, String cursor, String filters) {
        return "place:search:" +
                String.format("lat=%.6f:", lat) +
                String.format("lng=%.6f:", lng) +
                String.format("radius=%.1f:", radius) +
                "size=" + size + ':' +
                "cursor=" + (cursor == null ? "" : cursor) + ':' +
                "filters=" + (filters == null ? "" : filters.trim());
    }

	// [커서 생성 규칙] pageSize 기준 마지막 요소의 (distance, id)로 토큰 생성.
	//  - fetched <= pageSize → 다음 페이지 없음(null)
	//  - fetched  > pageSize → 다음 페이지 있음(token)
    private String buildNextCursor(List<PlaceSummary> summaries, int pageSize) {
        if (pageSize <= 0 || summaries.size() <= pageSize) {
            return null;
        }
        PlaceSummary last = summaries.get(pageSize - 1);
        return PlaceSearchCursor.toToken(last.distanceMeters(), last.placeId());
    }

	// [페이지 절단] 불변 리스트로 반환해 외부 변이 방지.
	private List<PlaceSummary> trimToPage(List<PlaceSummary> summaries, int pageSize) {
        if (pageSize <= 0) {
            return List.of();
        }
        if (summaries.size() <= pageSize) {
            return List.copyOf(summaries);
        }
        return List.copyOf(summaries.subList(0, pageSize));
    }
}
