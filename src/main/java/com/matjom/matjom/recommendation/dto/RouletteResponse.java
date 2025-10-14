package com.matjom.matjom.recommendation.dto;

import java.util.List;

/*
 * [비즈니스 응답 계약]
 * - placeId/name/lat/lng/distanceMeters: 선택된 “단 하나”의 후보(룰렛 결과).
 * - meta.candidateCount: 룰렛 직전 후보풀의 크기(UX 피드백: “반경 확대/축소 가이드” 판단 근거).
 * - meta.replayed: 멱등 저장소에서 **재생**된 응답인지(=60s 내 동일 요청) 표식.
 *   - true면 클라가 동일 응답을 재사용해 UI 깜빡임/변동을 줄일 수 있다.
 */
public record RouletteResponse(Long placeId,
                               String name,
                               double distanceMeters,
                               List<String> categories,
                               double latitude,
                               double longitude,
                               Meta meta) {

    public record Meta(int candidateCount, boolean replayed) {
        // 후보 수와 멱등 재생 여부
    }
}
