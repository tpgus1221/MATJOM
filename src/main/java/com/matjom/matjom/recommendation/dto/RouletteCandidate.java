package com.matjom.matjom.recommendation.dto;

import java.util.List;

/*
 * [룰렛 "후보풀"의 원소]
 * - RouletteService는 이 리스트에서 "균등 확률"로 1개를 뽑는다(가중치 없음이 기본 정책).
 * - distanceMeters: UI 표시에만 사용(선택 로직과 무관). 서버가 계산한 meter 단위를 그대로 노출.
 * - categories: 카테고리 필터/노출용. 스키마에 따라 단일 문자열일 수도 있고 다중일 수도 있음.
 */
public record RouletteCandidate(Long placeId,
                                String name,
                                double distanceMeters,
                                List<String> categories,
                                double latitude,
                                double longitude) {
} // Repository → Service 전달 객체
