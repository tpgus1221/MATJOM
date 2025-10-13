# UC-Stat-01 음식점 종합 통계 조회 API (9월 30일 최종)

## 엔드포인트 개요
- **HTTP Method**: `GET`
- **URL**: `/api/places/{placeId}/stats`
- **경로 변수**
  - `placeId` (`Long`, required): 통계를 확인할 장소 ID. 양수만 허용.
- **인증**: JWT 기반 사용자 인증 (기존 Feed 서비스와 동일 흐름).

## 동작 흐름
1. `StatisticsService`가 장소 존재 여부(`PlaceReadRepository#findNameById`)를 확인한다.
2. `StatisticsRepository`가 DB에서 통계 스냅샷을 계산한다.
   - `visits` 테이블에서 누적 도착(`arrived_at IS NOT NULL`) 인원과 11시~20시 모든 시간대에 대한 최근 14일(당일 포함) 평균 도착 인원을 구한다.
   - `daily_likes` 테이블에서 `status='ACTIVE'`인 좋아요 누적 수를 함께 반환한다.
3. 계산된 스냅샷을 `StatsResponseDTO`로 변환해 API 응답으로 전달한다.

## 응답 필드 정의
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `placeName` | `String` | 통계를 조회한 장소 이름 |
| `totalVisitors` | `long` | 누적 방문자 수 (`visits.arrived_at IS NOT NULL`) |
| `totalLikes` | `long` | 누적 좋아요 수 (`daily_likes.status = 'ACTIVE'`) |
| `hourlyArrivals` | `Array<HourlyArrival>` | 11시~20시까지 각 시간대별 최근 14일 평균 도착 인원 |
| `hourlyArrivals[].hour` | `int` | 시간대 시작 시각(24시간 기준, 11~20) |
| `hourlyArrivals[].averageCount` | `long` | 해당 시간대의 최근 14일 평균 도착 인원 |

> 내부 모니터링을 위해 `generatedAt`, `cacheTtlSeconds`, `dataSource` 값을 유지하지만 API 응답에는 포함하지 않습니다.

## 응답 예시
```json
{
  "success": true,
  "data": {
    "placeName": "홍대맛집",
    "totalVisitors": 120,
    "totalLikes": 45,
    "hourlyArrivals": [
      { "hour": 11, "averageCount": 8 },
      { "hour": 12, "averageCount": 5 },
      { "hour": 13, "averageCount": 3 },
      { "hour": 14, "averageCount": 4 },
      { "hour": 15, "averageCount": 6 },
      { "hour": 16, "averageCount": 5 },
      { "hour": 17, "averageCount": 7 },
      { "hour": 18, "averageCount": 9 },
      { "hour": 19, "averageCount": 6 },
      { "hour": 20, "averageCount": 4 }
    ]
  },
  "timestamp": "2025-09-30T08:30:00Z"
}
```

## 캐시 전략
- 자정 배치로 하루에 한 번 값이 갱신되므로 현재는 캐시를 사용하지 않고 매 요청마다 DB 스냅샷을 그대로 반환한다.
- 트래픽 증가나 실시간 갱신 요구가 생길 경우 Redis Cache-aside 패턴을 도입할 수 있도록 이전 설계안은 `docs/feed-moderation-statistics-handbook.md` 부록에 보관되어 있다.

## 통합 테스트 참고
- Postgres 기반 통합 테스트는 운영 DB 환경 확보 시 추가 예정. 현재는 단위 테스트로 로직을 검증.

## 향후 과제
- 오류 응답 규격 정리 및 문서화.
- 배치(`UC-Batch-01`)와 연계한 추가 지표 설계.
