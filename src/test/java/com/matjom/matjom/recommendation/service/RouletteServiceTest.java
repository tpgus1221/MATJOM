package com.matjom.matjom.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjom.matjom.common.exception.base.RecommendationException;
import com.matjom.matjom.common.idempotency.IdempotencyCallback;
import com.matjom.matjom.common.idempotency.IdempotencyResult;
import com.matjom.matjom.common.idempotency.IdempotencyStore;
import com.matjom.matjom.place.repository.PlaceRepository;
import com.matjom.matjom.recommendation.dto.RouletteCandidate;
import com.matjom.matjom.recommendation.dto.RouletteRequest;
import com.matjom.matjom.recommendation.dto.RouletteResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RouletteServiceTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private IdempotencyStore idempotencyStore;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RouletteService rouletteService;

    @BeforeEach
    void setUp() {
        rouletteService = new RouletteService(placeRepository, idempotencyStore, objectMapper);
        when(idempotencyStore.replayOrRun(anyString(), anyString(), eq(RouletteResponse.class), any()))
                .thenAnswer(new Answer<IdempotencyResult<RouletteResponse>>() {
                    @Override
                    public IdempotencyResult<RouletteResponse> answer(InvocationOnMock invocation) {
                        IdempotencyCallback<RouletteResponse> callback = invocation.getArgument(3);
                        RouletteResponse response = callback.execute();
                        return new IdempotencyResult<>(response, false);
                    }
                });
    }

    @Test
    void recommendSelectsCandidateDeterministicallyWithSeed() {
        RouletteRequest request = buildRequest();
        request.setSeed(42L);

        List<RouletteCandidate> candidates = List.of(
                candidate(1L, "A", 10.0, List.of("korean"), 37.5001, 127.0001),
                candidate(2L, "B", 20.0, List.of("japanese"), 37.5002, 127.0002),
                candidate(3L, "C", 30.0, List.of("chinese"), 37.5003, 127.0003)
        );
        when(placeRepository.findRouletteCandidates(anyDouble(), anyDouble(), anyDouble(), anyList(), anyInt()))
                .thenReturn(candidates);

        RouletteResponse response = rouletteService.recommend(request, "key-1");

        assertThat(response.placeId()).isEqualTo(3L);
        assertThat(response.latitude()).isEqualTo(37.5003);
        assertThat(response.longitude()).isEqualTo(127.0003);
        assertThat(response.meta().candidateCount()).isEqualTo(3);
        assertThat(response.meta().replayed()).isFalse();
    }

    @Test
    void recommendThrowsWhenNoCandidates() {
        RouletteRequest request = buildRequest();
        when(placeRepository.findRouletteCandidates(anyDouble(), anyDouble(), anyDouble(), anyList(), anyInt()))
                .thenReturn(List.of());

        boolean thrown = false;
        try {
            rouletteService.recommend(request, "key-2");
        } catch (RecommendationException expected) {
            thrown = true;
        }
        assertThat(thrown).isTrue();
    }

    @Test
    void recommendDistributionRemainsWithinFivePercentTolerance() {
        List<RouletteCandidate> candidates = List.of(
                candidate(1L, "A", 10.0, List.of("korean"), 37.5001, 127.0001),
                candidate(2L, "B", 20.0, List.of("japanese"), 37.5002, 127.0002),
                candidate(3L, "C", 30.0, List.of("chinese"), 37.5003, 127.0003)
        );
        when(placeRepository.findRouletteCandidates(anyDouble(), anyDouble(), anyDouble(), anyList(), anyInt()))
                .thenReturn(candidates);

        int totalRuns = 600;
        int[] counts = new int[]{0, 0, 0};
        int index;
        for (int i = 0; i < totalRuns; i++) {
            RouletteRequest request = buildRequest();
            request.setSeed((long) i);
            RouletteResponse response = rouletteService.recommend(request, "dist-" + i);
            long placeId = response.placeId();
            if (placeId == candidates.get(0).placeId()) {
                index = 0;
            } else if (placeId == candidates.get(1).placeId()) {
                index = 1;
            } else {
                index = 2;
            }
            counts[index] = counts[index] + 1;
            assertThat(response.meta().candidateCount()).isEqualTo(3);
            assertThat(response.meta().replayed()).isFalse();
            assertThat(response.latitude()).isBetween(37.5001, 37.5003);
            assertThat(response.longitude()).isBetween(127.0001, 127.0003);
        }

        int min = counts[0];
        int max = counts[0];
        for (int count : counts) {
            if (count < min) {
                min = count;
            }
            if (count > max) {
                max = count;
            }
        }
        int tolerance = (int) Math.round(totalRuns * 0.05);
        assertThat(max - min).isLessThanOrEqualTo(tolerance);
    }

    @Test
    void recommendMarksMetaAsReplayedWhenStoreReturnsCachedValue() {
        RouletteRequest request = buildRequest();
        RouletteResponse cached = new RouletteResponse(
                99L,
                "Cached",
                12.3,
                List.of("korean"),
                37.5010,
                127.0010,
                new RouletteResponse.Meta(5, false)
        );
        when(idempotencyStore.replayOrRun(anyString(), anyString(), eq(RouletteResponse.class), any()))
                .thenReturn(new IdempotencyResult<>(cached, true));

        RouletteResponse response = rouletteService.recommend(request, "key-3");

        assertThat(response.placeId()).isEqualTo(99L);
        assertThat(response.latitude()).isEqualTo(37.5010);
        assertThat(response.longitude()).isEqualTo(127.0010);
        assertThat(response.meta().candidateCount()).isEqualTo(5);
        assertThat(response.meta().replayed()).isTrue();
    }

    private RouletteRequest buildRequest() {
        RouletteRequest request = new RouletteRequest();
        request.setLat(37.5);
        request.setLng(127.0);
        request.setRadius(300.0);
        request.setLimit(50);
        request.setCategories(List.of("korean"));
        return request;
    }

    private RouletteCandidate candidate(long id,
                                        String name,
                                        double distance,
                                        List<String> categories,
                                        double lat,
                                        double lng) {
        return new RouletteCandidate(id, name, distance, categories, lat, lng);
    }
}
