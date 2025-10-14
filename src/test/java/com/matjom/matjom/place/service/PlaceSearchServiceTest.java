package com.matjom.matjom.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjom.matjom.common.exception.base.SearchException;
import com.matjom.matjom.place.dto.PlaceSearchCursor;
import com.matjom.matjom.place.dto.PlaceSearchRequest;
import com.matjom.matjom.place.dto.PlaceSearchResponse;
import com.matjom.matjom.place.dto.PlaceSearchResponse.PlaceSummary;
import com.matjom.matjom.place.repository.PlaceRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlaceSearchServiceTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PlaceSearchService placeSearchService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        placeSearchService = new PlaceSearchService(placeRepository, redisTemplate, objectMapper);
    }

    @Test
    void returnsCachedResultWhenHit() throws Exception {
        // given: 동일 조건의 응답이 이미 Redis 캐시에 존재한다
        PlaceSearchResponse cached = new PlaceSearchResponse(
                List.of(summary(1L, "Place", 123.4)),
                "cursor"
        );
        when(valueOperations.get(anyString())).thenReturn(objectMapper.writeValueAsString(cached));

        // when: 같은 조건으로 다시 검색하면
        PlaceSearchResponse result = placeSearchService.search(buildRequest());

        // then: 캐시가 그대로 반환되고 DB 조회도 캐시 쓰기도 실행되지 않는다
        assertThat(result).isEqualTo(cached);
        verify(placeRepository, never()).search(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), anyString());
        verify(valueOperations, never()).set(anyString(), anyString(), any());
    }

    @Test
    void cachesResultWhenMiss() throws Exception {
        // given: 캐시에 값이 없고 Repository가 정상 결과를 반환할 때
        when(valueOperations.get(anyString())).thenReturn(null);
        List<PlaceSummary> summaries = List.of(summary(2L, "Another", 45.6));
        when(placeRepository.search(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), any()))
                .thenReturn(summaries);

        // when: 검색을 수행하면
        PlaceSearchResponse response = placeSearchService.search(buildRequest());

        // then: DB 결과를 그대로 응답하고 TTL 60초로 캐시에 저장한다
        assertThat(response.places()).containsExactlyElementsOf(summaries);
        verify(placeRepository).search(anyDouble(), anyDouble(), anyDouble(), eq(21), isNull(), eq((String) null));
        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofSeconds(60)));
    }

    @Test
    void createsNextCursorWhenLimitReached() {
        // given: pageSize+1 만큼 결과가 존재해 다음 커서를 계산할 수 있는 상황
        when(valueOperations.get(anyString())).thenReturn(null);
        List<PlaceSummary> summaries = List.of(
                summary(10L, "First", 12.34567),
                summary(20L, "Second", 45.67891),
                summary(30L, "Third", 78.90123)
        );
        when(placeRepository.search(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), any()))
                .thenReturn(summaries);

        // when: pageSize를 2로 제한해 검색하면
        PlaceSearchRequest request = buildRequest();
        request.setSize(2);

        PlaceSearchResponse response = placeSearchService.search(request);

        // then: 2건만 내려주면서 3번째 행을 기준으로 nextCursor를 계산한다
        assertThat(response.places()).hasSize(2);
        List<Long> placeIds = new ArrayList<>();
        for (PlaceSummary summary : response.places()) {
            placeIds.add(summary.placeId());
        }
        assertThat(placeIds).containsExactly(10L, 20L);
        assertThat(response.nextCursor()).isEqualTo("45.67891:20");
    }

    @Test
    void doesNotCreateNextCursorWhenResultsLessThanRequestedSize() {
        // given: pageSize보다 적은 두 건만 검색되는 상황에서
        when(valueOperations.get(anyString())).thenReturn(null);
        List<PlaceSummary> summaries = List.of(
                summary(10L, "First", 12.34567),
                summary(20L, "Second", 45.67891)
        );
        when(placeRepository.search(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), any()))
                .thenReturn(summaries);

        // when: size를 크게 요청해도
        PlaceSearchRequest request = buildRequest();
        request.setSize(5);

        PlaceSearchResponse response = placeSearchService.search(request);

        // then: 다음 페이지가 없으므로 nextCursor는 null이다
        assertThat(response.places()).hasSize(2);
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void attachesTooManyResultsMetaWhenMoreThanMaxLimit() {
        // given: 최대 허용치(500)를 초과하는 501건 데이터셋을 반환하도록 세팅하고
        when(valueOperations.get(anyString())).thenReturn(null);
        List<PlaceSummary> summaries = new ArrayList<>();
        for (int i = 1; i <= 501; i++) {
            summaries.add(summary((long) i, "Place " + i, (double) i));
        }
        when(placeRepository.search(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), any()))
                .thenReturn(summaries);

        // when: 100개 페이지를 요청하면
        PlaceSearchRequest request = buildRequest();
        request.setSize(100);

        PlaceSearchResponse response = placeSearchService.search(request);

        // then: 응답 본문은 100건으로 제한되고 too_many_results 메타가 포함된다
        assertThat(response.places()).hasSize(100);
        assertThat(response.nextCursor()).isEqualTo(PlaceSearchCursor.toToken(100.0, 100L));
        assertThat(response.meta()).isNotNull();
        assertThat(response.meta().reason()).isEqualTo("too_many_results");
        assertThat(response.meta().suggest()).isNotBlank();
    }

    @Test
    void addsLowResultsSuggestionWhenResultsBelowThreshold() {
        // given: 검색 결과가 5건뿐인 상황(임계치 20 미만)
        when(valueOperations.get(anyString())).thenReturn(null);
        List<PlaceSummary> dataset = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            dataset.add(summary((long) i, "Place " + i, (double) i));
        }
        configureRepositoryDataset(dataset);

        // when: 기본 pageSize로 검색하면
        PlaceSearchResponse response = placeSearchService.search(buildRequest());

        // then: 결과 수가 적다는 안내(meta.reason=low_results)를 반환한다
        assertThat(response.places()).hasSize(5);
        assertThat(response.meta()).isNotNull();
        assertThat(response.meta().reason()).isEqualTo("low_results");
        assertThat(response.meta().suggest()).contains("반경");
    }

    @Test
    void paginatesAcrossPagesWithoutDuplicates() {
        // given: 25건의 연속 데이터를 준비하고
        when(valueOperations.get(anyString())).thenReturn(null);
        List<PlaceSummary> dataset = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            dataset.add(summary((long) i, "Place " + i, i * 10.0));
        }
        configureRepositoryDataset(dataset);

        // when: 커서를 따라 여러 페이지를 순차 호출하면
        List<Long> collectedIds = new ArrayList<>();
        PlaceSearchRequest request = buildRequest();
        int safety = 0;
        while (safety++ < 5) {
            PlaceSearchResponse response = placeSearchService.search(request);
            for (PlaceSummary summary : response.places()) {
                collectedIds.add(summary.placeId());
            }

            if (response.nextCursor() == null) {
                break;
            }
            request = buildRequest();
            request.setCursor(response.nextCursor());
        }

        // then: 중복 없이 전 데이터셋을 정확히 복원할 수 있다
        assertThat(collectedIds).doesNotContainNull();
        assertThat(collectedIds).doesNotHaveDuplicates();
        List<Long> expectedIds = new ArrayList<>();
        for (PlaceSummary summary : dataset) {
            expectedIds.add(summary.placeId());
        }
        assertThat(collectedIds).containsExactlyElementsOf(expectedIds);
    }

    @Test
    void parsesCursorAndPassesToRepository() {
        // given: 커서 문자열을 포함한 요청에서
        when(valueOperations.get(anyString())).thenReturn(null);
        when(placeRepository.search(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), any()))
                .thenReturn(List.of());

        PlaceSearchRequest request = buildRequest();
        request.setCursor("123.45:99");

        // when: 서비스를 호출하면
        placeSearchService.search(request);

        // then: DTO가 커서를 파싱해 Repository까지 그대로 전달한다
        ArgumentCaptor<PlaceSearchCursor> cursorCaptor = ArgumentCaptor.forClass(PlaceSearchCursor.class);
        verify(placeRepository).search(anyDouble(), anyDouble(), anyDouble(), anyInt(), cursorCaptor.capture(), eq((String) null));
        assertThat(cursorCaptor.getValue().distanceMeters()).isEqualTo(123.45);
        assertThat(cursorCaptor.getValue().lastPlaceId()).isEqualTo(99L);
    }

    @Test
    void throwsExceptionWhenCursorInvalid() {
        // given: 정해진 포맷을 벗어난 커서 문자열을 전달하고
        PlaceSearchRequest request = buildRequest();
        request.setCursor("invalid");

        // when: 검색을 시도하면 커서 검증이 실패한다
        SearchException caught = null;
        try {
            placeSearchService.search(request);
        } catch (SearchException ex) {
            caught = ex;
        }
        // then: INVALID_REQUEST_PARAM 메시지를 담은 예외가 발생한다
        assertThat(caught).isNotNull();
        assertThat(caught.getMessage()).contains("cursor");
    }

    private PlaceSearchRequest buildRequest() {
        PlaceSearchRequest request = new PlaceSearchRequest();
        request.setLat(37.5665);
        request.setLng(126.9780);
        // radius와 size는 null이면 기본값을 사용
        return request;
    }

    private static PlaceSummary summary(long id, String name, double distanceMeters) {
        double baseLat = 37.0 + (id % 1000) * 0.0001;
        double baseLng = 127.0 + (id % 1000) * 0.0001;
        return new PlaceSummary(id, name, distanceMeters, baseLat, baseLng);
    }

    private void configureRepositoryDataset(List<PlaceSummary> dataset) {
        when(placeRepository.search(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), any()))
                .thenAnswer(new Answer<List<PlaceSummary>>() {
                    @Override
                    public List<PlaceSummary> answer(InvocationOnMock invocation) {
                        int limit = invocation.getArgument(3);
                        PlaceSearchCursor cursor = invocation.getArgument(4);
                        double cursorDistance = cursor == null ? Double.NEGATIVE_INFINITY : cursor.distanceMeters();
                        long cursorId = cursor == null ? Long.MIN_VALUE : cursor.lastPlaceId();

                        List<PlaceSummary> results = new ArrayList<>();
                        for (PlaceSummary summary : dataset) {
                            boolean beyondCursorDistance = summary.distanceMeters() > cursorDistance;
                            boolean sameDistanceHigherId = summary.distanceMeters() == cursorDistance && summary.placeId() > cursorId;
                            if (beyondCursorDistance || sameDistanceHigherId) {
                                results.add(summary);
                            }
                            if (results.size() >= limit) {
                                break;
                            }
                        }
                        return results;
                    }
                });
    }
}
