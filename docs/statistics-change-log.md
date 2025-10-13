# Statistics 패키지 구현/테스트 정리 (2025-09-30)

## UC-Stat-01: `GET /api/places/{placeId}/stats`
- DTO/스냅샷: `StatsResponseDTO`, `StatsSnapshot`을 통해 누적 방문/좋아요/11~20시 시간대별 평균 도착 수를 전달한다.
- 저장소: `StatisticsRepository` 네이티브 쿼리로 `visits`/`daily_likes`를 집계하며, 11~20시 모든 시간대의 최근 14일(당일 포함) 평균 도착 인원을 계산한다.
- 서비스: `StatisticsService`가 장소 존재 여부를 확인한 뒤 DB 스냅샷을 계산해 그대로 응답한다 (캐시 사용 없음).
- 컨트롤러: `StatisticsController`에 `GET /api/places/{placeId}/stats` 엔드포인트 추가.
- 테스트: `StatisticsServiceTest`, `StatisticsControllerTest`로 서비스/컨트롤러 로직 검증.

## 기타 보완 사항
- 테스트에서 Spring Security 필터를 비활성화(`@AutoConfigureMockMvc(addFilters = false)`)해 인증 실패를 방지.
- `tasks/statistics-task-plan.md`에 실행된 작업/테스트/모니터링 정의를 최신화했고, 향후 수행할 통합 테스트 계획을 명시.
  (2025-10-XX) 통계는 실시간 스냅샷만 사용하기로 결정해 일간 배치 구성요소를 제거하였다.

## 테스트 현황 (2025-10 기준)
- `statistics.controller`: `StatisticsControllerTest`
- `statistics.service`: `StatisticsServiceTest`
- `place.controller`: `PlaceControllerTest`
- `feed.service`: `PlaceDetailServiceTest`

위 단위 테스트는 `./gradlew test`로 주기적으로 확인하며, 실환경(Postgres) 통합 테스트는 운영 DB 계층 준비 후 별도 계획으로 진행한다.
