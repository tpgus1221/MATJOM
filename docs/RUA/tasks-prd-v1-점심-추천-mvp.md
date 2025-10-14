## Relevant Files

- `src/main/java/com/matjom/matjom/place/api/PlaceController.java` - 장소 검색 API(v1) 엔드포인트.
- `src/main/java/com/matjom/matjom/place/service/PlaceSearchService.java` - PostGIS 반경/거리 ASC + 커서 페이징 핵심 로직.
- `src/main/java/com/matjom/matjom/place/repository/PlaceRepository.java` - PostGIS 질의(Native/Querydsl) 및 커서 지원.
- `src/main/java/com/matjom/matjom/place/dto/PlaceSearchDtos.java` - 요청/응답 DTO와 커서 토큰 직렬화(`distance,lastId`).
- `src/main/java/com/matjom/matjom/recommendation/api/RouletteController.java` - 룰렛 API 엔드포인트.
- `src/main/java/com/matjom/matjom/recommendation/service/RouletteService.java` - 후보군 생성/균등 선택/멱등 60s 재생.
- `src/main/java/com/matjom/matjom/visit/api/VisitSessionController.java` - 세션 시작/위치/도착/취소 API.
- `src/main/java/com/matjom/matjom/visit/service/VisitSessionService.java` - 도착 판정(30m+3분, 유예 10s)/타임아웃 30분.
- `src/main/java/com/matjom/matjom/visit/service/GeoFenceEvaluator.java` - `ST_DWithin`, dwell 타이머, 정확도 가드.
- `src/main/java/com/matjom/matjom/visit/repository/VisitRepository.java` - 세션 상태 저장/조회, UNIQUE(state='ACTIVE').
- `src/main/java/com/matjom/matjom/visit/repository/VisitPositionRepository.java` - 위치 이벤트 적재/조회.
- `src/main/java/com/matjom/matjom/common/ratelimit/RateLimitConfig.java` - bucket4j + Redis 레이트리밋 규칙.
- `src/main/java/com/matjom/matjom/common/ratelimit/RateLimitFilter.java` - 키 스킴(userId/IP) 적용 필터.
- `src/main/java/com/matjom/matjom/common/idempotency/IdempotencyFilter.java` - `Idempotency-Key` 60s 응답 재생 인터셉터.
- `src/main/java/com/matjom/matjom/common/idempotency/IdempotencyStore.java` - Redis 저장소(본문+헤더 보관).
- `src/main/java/com/matjom/matjom/common/security/JwtSecurityConfig.java` - RS256 검증, `traceparent` 허용.
- `src/main/java/com/matjom/matjom/common/security/JwksController.java` - `GET /oauth/jwks.json`(캐시 3600s, `kid`).
- `src/main/java/com/matjom/matjom/common/security/KeyRotationService.java` - 90d 회전/7d 오버랩 로테이터(초안).
- `src/main/java/com/matjom/matjom/common/observability/TracingConfig.java` - W3C tracecontext 수용.
- `src/main/java/com/matjom/matjom/common/observability/MetricsConfig.java` - `search_p95, arrive_rate` 등 Micrometer 메트릭.
- `src/main/resources/application.yml` - 반경/체류/간격/TTL/상한 등 파라미터 외부화.
- `src/main/resources/schema-postgres.sql` - PostGIS 타입/인덱스 보강.
- `docs/openapi/openapi-v1.yaml` - v1 스펙(커서/429/멱등/상한 500 안내) 반영.
- `docs/RUA/task-notes/session-lifecycle-main-issue.md` - 4.x 메인 이슈 및 서브 이슈 관리 문서.
- `docs/RUA/task-notes/session-lifecycle-task-guide.md` - 세션 라이프사이클 사전 학습 가이드.
- `docs/RUA/task-notes/session-lifecycle-delivery-summary.md` - 4.x 완료 후 결과 요약 템플릿.
- `docs/RUA/task-notes/session-lifecycle-issue-24-2-prep.md` - 4.2 위치 수신 선행 학습 문서.
- `docs/RUA/task-notes/session-lifecycle-issue-24-2-impl.md` - 4.2 위치 수신 구현 요약.
- `docs/RUA/task-notes/session-lifecycle-issue-24-3-prep.md` - 4.3 GeoFenceEvaluator 선행 학습 문서.
- `docs/RUA/task-notes/session-lifecycle-issue-24-3-impl.md` - 4.3 GeoFenceEvaluator 구현 요약.
- `docs/RUA/task-notes/session-lifecycle-issue-24-4-prep.md` - 4.4 정확도 가드 선행 학습 문서.
- `docs/RUA/task-notes/session-lifecycle-issue-24-4-impl.md` - 4.4 정확도 가드 구현 요약.
- `docs/RUA/task-notes/session-lifecycle-issue-24-5-prep.md` - 4.5 수동 도착 선행 학습 문서.
- `docs/RUA/task-notes/session-lifecycle-issue-24-5-impl.md` - 4.5 수동 도착 구현 요약.
- `docs/RUA/task-notes/session-lifecycle-issue-24-6-prep.md` - 4.6 타임아웃 스케줄러 선행 학습 문서.
- `docs/RUA/task-notes/session-lifecycle-issue-24-6-impl.md` - 4.6 타임아웃 스케줄러 구현 요약.
- `docs/RUA/task-notes/session-lifecycle-issue-24-7-prep.md` - 4.7 상태 전이 이벤트 선행 학습 문서.
- `docs/RUA/task-notes/session-lifecycle-issue-24-7-impl.md` - 4.7 상태 전이 이벤트 구현 요약.
- `docs/RUA/task-notes/session-lifecycle-issue-24-8-prep.md` - 4.8 경계 테스트 선행 학습 문서.
- `docs/RUA/task-notes/session-lifecycle-issue-24-8-impl.md` - 4.8 경계 테스트 구현 요약.
- `docs/RUA/future/visit-audit-metadata-guide.md` - Visit 감사 메타 적재 향후 적용 가이드.
- `docs/RUA/future/visit-timeout-distributed-lock-guide.md` - 타임아웃 스케줄러 분산 락 향후 적용 가이드.
- `docs/RUA/future/visit-expiration-event-guide.md` - 방문 만료 이벤트 기록·노출 향후 적용 가이드.
- `docs/RUA/future/visit-state-event-streaming-guide.md` - 상태 전이 이벤트 스트리밍 설계 가이드.
- `docs/RUA/future/visit-state-event-consumer-guide.md` - 상태 전이 이벤트 소비자 설계 가이드.
- `docs/adr/ADR-00X-arrival-policy-30m-3min.md` - 도착 판정·유예 정책 근거.
- `infra/grafana/dashboards/search-session-auth.json` - 대시보드 JSON.
- `src/main/java/com/matjom/matjom/visit/api/VisitSessionController.java` - 세션 시작 API 엔드포인트.
- `src/main/java/com/matjom/matjom/visit/api/VisitSessionController.java` - 세션 시작/위치 수신 API 엔드포인트.
- `src/main/java/com/matjom/matjom/visit/service/VisitSessionService.java` - 세션 시작 멱등/검증 비즈니스 로직.
- `src/main/java/com/matjom/matjom/visit/service/VisitPositionService.java` - 위치 이벤트 저장 및 지오펜스 연동.
- `src/main/java/com/matjom/matjom/visit/dto/VisitSessionStartRequest.java` - 세션 시작 요청 DTO.
- `src/main/java/com/matjom/matjom/visit/dto/VisitSessionStartResponse.java` - 세션 시작 응답 DTO.
- `src/main/java/com/matjom/matjom/visit/dto/VisitPositionRequest.java` - 위치 이벤트 요청 DTO.
- `src/main/java/com/matjom/matjom/visit/dto/VisitPositionResponse.java` - 위치 이벤트 응답 DTO.
- `src/main/java/com/matjom/matjom/visit/repository/VisitPositionRepository.java` - 위치 이벤트 저장 리포지토리.
- `src/main/java/com/matjom/matjom/visit/geofence/GeoFenceEvaluator.java` - 지오펜스 평가 인터페이스.
- `src/main/java/com/matjom/matjom/visit/geofence/GeoFenceEvaluationResult.java` - 지오펜스 평가 결과 객체.
- `src/main/java/com/matjom/matjom/visit/geofence/DefaultGeoFenceEvaluator.java` - 30m/180s/10s 로직을 적용한 평가 구현.
- `src/main/java/com/matjom/matjom/visit/geofence/DefaultGeoFenceEvaluator.java` - 30m/180s/10s + 정확도 가드 로직.
- `src/main/java/com/matjom/matjom/place/repository/PlaceJpaRepository.java` - 장소 조회용 Spring Data 리포지토리.
- `src/main/java/com/matjom/matjom/user/repository/UserRepository.java` - 사용자 조회용 Spring Data 리포지토리.
- `src/test/java/com/matjom/matjom/visit/service/VisitSessionServiceTest.java` - 세션 시작 서비스 단위 테스트.
- `src/test/java/com/matjom/matjom/visit/service/VisitPositionServiceTest.java` - 위치 이벤트 서비스 단위 테스트.
- `src/test/java/com/matjom/matjom/visit/geofence/DefaultGeoFenceEvaluatorTest.java` - 지오펜스 평가 로직 단위 테스트.

- `src/test/java/com/matjom/matjom/place/PlaceSearchServiceTest.java` - 검색 커서/상한/캐시 단위·통합 테스트.
- `src/test/java/com/matjom/matjom/recommendation/service/RouletteServiceTest.java` - 균등성/멱등 재생/분포 테스트.
- `src/test/java/com/matjom/matjom/common/idempotency/InMemoryIdempotencyStoreTest.java` - 멱등 재생/충돌 시나리오 검증.
- `src/test/java/com/matjom/matjom/visit/GeoFenceEvaluatorTest.java` - 30m/3분/유예 10s 경계 테스트.
- `src/test/java/com/matjom/matjom/common/idempotency/IdempotencyFilterTest.java` - 재생/충돌 테스트.
- `src/test/java/com/matjom/matjom/common/ratelimit/RateLimitFilterTest.java` - 429 + `Retry-After` 헤더 테스트.
- `src/test/java/com/matjom/matjom/common/security/JwksControllerTest.java` - 캐시 헤더/kid 회전 테스트.
- `sql/migration/001_places_location_to_geography.sql` - `places.location`을 **GEOGRAPHY(Point,4326)**로 변환하는 안전 마이그레이션 스크립트(타입/좌표계 정규화, GiST 인덱스 재생성, 검증 쿼리 포함).



### Notes

- 테스트는 Testcontainers(PostGIS 16-3.4, Redis)로 격리 실행.
- PostGIS 컬럼은 **GEOGRAPHY(Point,4326)** 사용 권장(미터 단위). 현재 geometry면 마이그레이션 전략 포함.
- Redis 키 예:  
  - 레이트리밋: `rl:{route}:{scope}:{userId|ip}`  
  - 멱등키: `idemp:{key}` → `status, headers, body, ttl`  
  - 좋아요 카운트: `cnt:like:{user}:{place}:{yyyyMMdd}`  
- 커서 토큰 직렬화: `"{distanceMeters}:{lastId}"` (예: `"123.45:98765"`), 서버 검증 필수.

## Tasks

- [x] 1.0 PostGIS 스키마/인덱스 정합화
  - [x] 1.1 `places.location` 타입을 `GEOGRAPHY(Point,4326)`로 확정(현재 geometry면 `ALTER TABLE` + 데이터 변환 스크립트 작성).
  - [x] 1.2 인덱스 점검/생성: `GIST(location)`, `btree(lower(name))`, 카테고리 `GIN` 유지. (2025-09-19 확인 완료 — 상세 설명: `docs/RUA/explanations/indexing-primer.md` 참고)
  - [x] 1.3 거리 계산 표준화: `ST_DWithin(geom, point, 30)`/`ST_Distance(geom, point)`를 **미터 단위**로 반환하도록 SQL 템플릿 확정. (2025-09-19, `docs/RUA/explanations/distance-standard.md` 참고)
  - [x] 1.4 샘플 데이터 1k건 적재 스크립트 작성(`sql/import_initial_places.sql`) 및 질의 p95 베이스라인 캡처. (2025-09-19, 결과: p95=559.13m, 상세: `docs/RUA/explanations/search-baseline.md`)

- [ ] 2.0 검색 API(v1)
  - [x] 2.1 `PlaceController.getPlaces()` 스켈레톤 + 요청 파라미터 검증(`lat,lng,radius,size,cursor,filters`).
  - [x] 2.2 `PlaceSearchService.search()`에서 캐시(60s) 선조회 → 미스 시 PostGIS 질의 수행. (`PlaceRepository` native 질의 + Redis 60s 캐시)
  - [x] 2.3 커서 페이징 구현: 정렬 `distance ASC, id ASC`; 커서(`distance,lastId`) 파싱/검증/다음 커서 생성.
  - [x] 2.4 결과 상한 500 처리: 501+면 `200` + `meta.reason="too_many_results"` + `suggest`.
  - [x] 2.5 레이트리밋(10/10s user/ip) 적용 및 `Retry-After` 헤더 세팅.
  - [x] 2.6 OpenAPI 문서에 커서/상한/메타 필드/429 규약 반영.
  - [x] 2.7 단위/통합 테스트: 무중복/무누락, 커서 경계, `<20건` 필터 제안, `>500건` 메시지.

- [ ] 3.0 룰렛 API(v1)
  - [x] 3.1 `RouletteController.postRoulette()` + `Idempotency-Key` 헤더 필수 검증.
  - [x] 3.2 `RouletteService`에서 조건별 후보 조회 + 서버 측 균등 무작위 선택.
  - [x] 3.3 멱등 재생: 동일 파라미터+멱등키 60s 재호출 시 동일 응답 반환(백엔드 저장소).
  - [x] 3.4 (옵션) `seed` 파라미터 지원으로 재현 가능한 추천 제어.
  - [x] 3.5 테스트/문서화: 무작위 분포 ±5%, 멱등 재생, OpenAPI 스펙 업데이트.

- [ ] 4.0 세션 라이프사이클
  - [x] 4.1 `VisitSessionController.start()` 구현: 멱등키 필수, 중복 ACTIVE 방지(트랜잭션/UNIQUE 제약).
  - [x] 4.2 `VisitSessionController.postLocation()` 구현: 위치 이벤트 수신 + `GeoFenceEvaluator` 호출.
  - [x] 4.3 `GeoFenceEvaluator` 구현: 30m 내 판정, dwell 타이머 시작/정지, **유예 10s** 내 재진입 시 누적 유지 로직.
  - [x] 4.4 정확도 가드: `accuracy_m>30`이면 dwell 카운트 **일시 정지**(포지션 기록은 지속).
  - [x] 4.5 `arrivals`(수동) 구현: 반경 ≤30m & 시작 10~60분 사이 유효성 검사 후 `arrived_at`.
  - [x] 4.6 타임아웃 스케줄러: `started_at + 30m` 초과 시 `EXPIRED` 전이.
  - [x] 4.7 상태 전이 이벤트 로깅(`event=ARRIVED|EXPIRED|CANCELLED`) 및 당일 권한 부여 트리거.
  - [x] 4.8 테스트: 29m/2.9m 미도착, 30m/3.0m 도착, 이탈 9s 연속/11s 리셋, 29분대 도착 허용.

- [ ] 5.0 위치 수용 간격 가드
  - [ ] 5.1 `VisitSessionController.postLocation()` 진입 전 필터에서 모드별 간격 체크(`auto≥25s`, `manual≥5s`).
  - [ ] 5.2 위반 시 429 + `Retry-After` 헤더(남은 초) 반환.
  - [ ] 5.3 최근 수신 시각을 Redis `lastloc:{sessionId}`에 저장(모드별 키 분리).
  - [ ] 5.4 테스트: auto 20s/24s 거부, 25s 수용 / manual 4s 거부, 5s 수용.

- [ ] 6.0 레이트리밋 인프라
  - [ ] 6.1 `RateLimitConfig`에서 route별 버킷 정책 정의(`/places` 10/10s, `/sessions*` 5/10s, `/auth/login` 5/60s).
  - [ ] 6.2 `RateLimitFilter`에서 키 스킴 구현(userId 우선, 없으면 IP; **양쪽 모두 적용** 가능).
  - [ ] 6.3 헤더 출력(`X-RateLimit-Remaining`, `Retry-After`) 표준화.
  - [ ] 6.4 테스트: 임계 초과 시 429, 헤더 값 검증.

- [ ] 7.0 멱등 인프라
  - [ ] 7.1 `IdempotencyFilter` 구현: 대상 라우트(`/sessions` `POST`, `/recommendations/roulette`)에서 요청 해시 생성.
  - [ ] 7.2 `IdempotencyStore`(Redis)에 `status, headers, body` 저장/TTL=60s.
  - [ ] 7.3 충돌/재생 분기(`409` vs 재생) 정책 확정 및 문서화.
  - [ ] 7.4 테스트: 같은 키 재호출 시 본문/헤더 완전 동일 재생.

- [ ] 8.0 JWT RS256 + JWKS
  - [ ] 8.1 `JwtSecurityConfig`에서 RS256 검증 및 클럭 스큐 ±60s 허용.
  - [ ] 8.2 `JwksController` 구현: `GET /oauth/jwks.json` + `Cache-Control: max-age=3600`.
  - [ ] 8.3 `KeyRotationService` 초안: 90d 회전, 7d 오버랩(`kid` 2개 노출 기간) 시나리오.
  - [ ] 8.4 로그인 실패 5회/60s 시 캡차 트리거 훅(추후 연동 포인트) 마련.
  - [ ] 8.5 테스트: JWKS 캐시/`kid` 교체 롤오버, 만료 토큰 거부.

- [ ] 9.0 관측성
  - [ ] 9.1 `TracingConfig`로 W3C `traceparent` 수용, 로그 MDC(`traceId`, `sessionId`, `userId`, `placeId`, `event`, `mode`, `radius`, `dwell`) 추가.
  - [ ] 9.2 `MetricsConfig`: `search_p95`, `arrive_rate`, `rate_limit_block`, `idempotency_replay` 메트릭 계측.
  - [ ] 9.3 Grafana 대시보드 JSON(`infra/grafana/...`)에 3뷰(검색/세션/인증) 패널 구성.
  - [ ] 9.4 알림 룰: `arrive_rate<0.6(5m)`, `search_5xx>1%` 알림 채널 연동.

- [ ] 10.0 OpenAPI v1 스펙
  - [ ] 10.1 `/api/v1/places` 요청/응답(커서 메타, `reason`, `suggest`) 스키마 정의.
  - [ ] 10.2 `/api/v1/recommendations/roulette` 멱등 헤더/응답 스키마 정의.
  - [ ] 10.3 `/api/v1/sessions/*`(start/locations/arrivals/cancel) 규격화 + 429/409/200/201 예시 추가.
  - [ ] 10.4 에러 코드 표(`code/http/message`) 정합성 검토 및 문서화.

- [ ] 11.0 테스트 전략 구현
  - [ ] 11.1 Geo 경계 테스트: 29.9m/179s 미도착, 30.0m/180s 도착, 이탈 9s 유지/11s 리셋.
  - [ ] 11.2 커서 무중복/무누락: 3페이지 연속 호출 시 일관성 검증, 마지막 `hasMore=false`.
  - [ ] 11.3 상한 500: 501건 시 `meta.reason="too_many_results"` 포함 확인.
  - [ ] 11.4 룰렛 분포/멱등: 1000회 분포 ±5%, 동일 키 동일 응답.
  - [ ] 11.5 간격 가드/레이트리밋: 429 + `Retry-After` 헤더 값 정확성.
  - [ ] 11.6 좋아요 제한: 1회/장소/일, 자정(KST) TTL 리셋 검증.

- [ ] 12.0 CI 통합
  - [ ] 12.1 GitHub Actions에서 PostGIS/Redis services 기동, Testcontainers 캐시 최적화.
  - [ ] 12.2 OpenAPI Lint(예: `redocly lint`) 및 스키마 빌드 잡 추가.
  - [ ] 12.3 간단 부하 스모크(예: `wrk` or JMH 대체) p95 수집/게이트 적용.

- [ ] 13.0 구성 외부화
  - [ ] 13.1 `application-*.yml`에 파라미터 외부화: `GEO_RADIUS_M=30`, `GEO_DWELL_MIN=3`, `REENTRY_GRACE_SEC=10`, `SESSION_TIMEOUT_MIN=30`, `SEARCH_CACHE_TTL=60s`, `MAX_RESULTS_PER_SEARCH=500`.
  - [ ] 13.2 모드별 수용 간격: `auto.accept.minSec=25`, `manual.accept.minSec=5`.
  - [ ] 13.3 레이트리밋 정책/멱등 TTL/캡차 임계 등 프로퍼티화.

- [ ] 14.0 계약/연동 문서화
  - [ ] 14.1 클라이언트 가이드: 업데이트 모드 UX, 수동 도착 노출 조건(≤30m & 10~60분), 429 재시도 전략.
  - [ ] 14.2 ADR 업데이트: 도착 정책(30m+3분+유예10s), 커서 선택 근거, 상한 500 UX.
  - [ ] 14.3 런북: 장애 시 토글(상한 안내/캡차/룰렛), 키/스키마 롤백 절차.


### 1.1 GEOGRAPHY 전환 — 산출물 & 주니어 개발 지시

**산출물**
- `sql/migration/001_places_location_to_geography.sql` 생성 (아래 지시를 따를 것)

**주니어 개발 지시 (Step-by-Step)**  
1) **로컬 PostGIS 준비**  
   - Docker로 실행 중이면 포트 `5432`가 열려있는지 확인. DB: `matjom_dev`, 유저: `devuser`.
2) **사전 점검**  
   - `\d+ places` 로 `location` 컬럼 타입 확인(geometry/geography 여부, SRID 확인).  
   - 기존 공간 인덱스(예: `idx_places_location*`) 존재 여부 체크.
3) **마이그레이션 실행**  
   - 아래 스크립트를 `psql -f sql/migration/001_places_location_to_geography.sql`로 실행.  
   - 오류 없을 것. 실패 시 트랜잭션 롤백 확인.
4) **검증 쿼리**  
   - `SELECT data_type FROM information_schema.columns WHERE table_name='places' AND column_name='location';` → `USER-DEFINED` + `geography`여야 함.  
   - `SELECT ST_SRID(location::geometry) FROM places LIMIT 5;` → 전부 `4326`.  
   - 샘플 거리: `SELECT ST_Distance(location, ST_MakePoint(127.0276,37.4979)::geography) FROM places LIMIT 1;` → **미터 단위** 값 반환.
5) **성능 점검**  
   - `EXPLAIN ANALYZE SELECT id FROM places WHERE ST_DWithin(location, ST_MakePoint(127.0276,37.4979)::geography, 30);`  
   - 계획에 `Index Cond`로 GiST 인덱스 활용되는지 확인.
6) **완료 조건 (AC)**  
   - 컬럼 타입이 `geography(Point,4326)`로 변환됨.  
   - GiST 인덱스 생성 완료.  
   - 거리/반경 함수에서 **미터 단위**로 정상 동작.

> 참고: 1.2(인덱스 보강)는 별도 태스크로 이어서 진행. 이 단계에서는 **location 컬럼 타입 전환**과 최소 GiST 인덱스까지만 수행.
