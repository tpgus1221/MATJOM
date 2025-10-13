# Statistics 패키지 정리 (최신)

## 1. 핵심 요약
- 사용자-facing 통계는 `/api/places/{placeId}/stats` 단일 API로 제공한다.
- 응답은 누적 방문자 수(ARRIVAL 기준), 누적 좋아요 수, 최근 14일간 11~20시 시간대별 평균 도착자 수를 포함한다.
- 장소 상세 화면은 `PlaceDetailService`가 통계와 리뷰를 조합해 복원력 있는 통합 응답을 만든다.
- 모든 수치는 DB 스냅샷을 직접 조회하며, 별도의 일간 배치나 캐시는 사용하지 않는다.

## 2. 처리 흐름

| 단계 | 요청/작업 | 주요 컴포넌트 | 설명 |
| --- | --- | --- | --- |
| A | 통계 API 호출 | `StatisticsController` → `StatisticsService` | 장소 존재 여부를 검증 후 즉시 스냅샷 조회 |
| B | 장소 상세 조회 | `PlaceController` → `PlaceDetailService` | 통계·리뷰를 결합하고 부분 실패 시 `errors` 필드에 기록 |

## 3. 통계 API 구현 (`GET /api/places/{placeId}/stats`)
- **Repository**: `StatisticsRepository`
  - `visits` 테이블에서 `arrived_at`이 있는 레코드만 세어 누적 방문 수를 계산한다.
  - `daily_likes` 테이블에서 `status = 'ACTIVE'`인 항목을 세어 누적 좋아요 수를 구한다.
  - 11~20시 범위에 대해 최근 14일 도착 건수를 평균 내어 시간대별 수치를 만든다.
- **Service**: `StatisticsService`
  - `PlaceReadRepository`로 장소 존재/이름을 확인한다.
  - 위 스냅샷을 `StatsResponseDTO`로 변환한다.
- **DTO**: `StatsResponseDTO`
  - `hourlyArrivals`를 11~20시 고정 길이 리스트로 보정하여 프런트가 Skeleton 없이 렌더링할 수 있게 한다.

## 4. 장소 상세 조합 (`GET /api/places/{placeId}`)
- 컨트롤러는 `reviewLimit` 파라미터를 받아 `PlaceDetailService`에 위임한다.
- 서비스는 필수 정보(장소 기본 정보)를 확보한 뒤 통계/리뷰 호출을 각기 `try/catch`로 감싸고 실패 시 `errors` 맵에 코드를 채운다.
- 리뷰 제한 값이 0 이하라면 전체를 반환하고, 양수면 지정 개수까지만 잘라낸다.

## 5. 테스트 & 검증
- **Controller**: `StatisticsControllerTest`, `PlaceControllerTest`
- **Service**: `StatisticsServiceTest`, `PlaceDetailServiceTest`
- 모두 `./gradlew test --tests …` 명령으로 통과 확인.
- Postgres 실환경 통합 테스트는 추후 운영 DB 계층이 준비되면 진행한다.

## 6. 다음 액션 제안
1. OpenAPI·핸드북 문서를 이번 구조에 맞춰 정리 (배치 관련 섹션 제거).
2. 시간대 평균 계산에 대한 쿼리 성능 모니터링을 위해 실행 계획 캐시 또는 인덱스 검토.
3. 필요 시 11~20시 외 구간 확장을 위한 파라미터화된 쿼리 플래닝 검토.
