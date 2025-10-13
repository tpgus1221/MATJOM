# MatJom Development Handbook

> _목적_: 프로젝트를 처음 세팅하는 개발자가 **PostgreSQL + Redis 환경 구축 → 스키마 적용 → 테스트 실행**까지 전 과정을 스스로 재현할 수 있도록 단계별로 안내합니다. 각 단계는 실제로 수행했던 명령과 함께 주의사항을 포함합니다.

---

## 1. 필수 도구 점검

| 도구 | 확인 명령 | 기대 결과 |
| ---- | --------- | ---------- |
| Docker | `docker -v` | 버전 정보 출력 (예: Docker version 24.x) |
| Java | `java -version` | JDK 17 이상 |
| Gradle Wrapper | `./gradlew -v` | 프로젝트 내 wrapper 동작 확인 |

> **TIP:** macOS/리눅스 사용자는 Docker Desktop 또는 동일 기능을 제공하는 런타임이 설치되어 있어야 합니다.

---

## 2. Postgres(PostGIS) 컨테이너 실행

### 2.1 컨테이너 생성

```bash
docker run -d \
  --name matjom-postgres \
  -p 5432:5432 \
  -e POSTGRES_USER=matjom \
  -e POSTGRES_PASSWORD=matjom \
  -e POSTGRES_DB=matjom_db \
  postgis/postgis:15-3.4
```

| 파라미터 | 의미 |
| -------- | ---- |
| `postgis/postgis:15-3.4` | PostGIS가 포함된 PostgreSQL 15 이미지 (geometry 타입 사용 가능) |
| `-p 5432:5432` | 로컬 5432 포트를 컨테이너 5432에 매핑 |
| 환경변수 | DB 접속 계정 (사용자/비밀번호/기본 DB) |

실행 후 `docker ps` 로 컨테이너 상태를 확인합니다.

### 2.2 스키마 적용

`src/main/resources/schema-postgres.sql` 은 모든 테이블/인덱스를 정의합니다. 아래 명령으로 컨테이너 내부 `psql`에 스키마 파일을 입력합니다.

```bash
cat src/main/resources/schema-postgres.sql | \
  docker exec -i matjom-postgres psql -U matjom -d matjom_db
```

정상 실행 시 콘솔에 `CREATE TABLE`, `CREATE INDEX` 등이 연속으로 출력됩니다. geometry 타입 오류가 발생한다면 PostGIS 이미지가 아닌 일반 postgres 이미지를 사용한 것인지 확인하세요.

---

## 3. Redis 컨테이너 실행

```bash
docker run -d \
  --name matjom-redis \
  -p 6379:6379 \
  redis:7-alpine
```

| 포트 | 설명 |
| ---- | ---- |
| 6379 | Spring RedisTemplate이 접근하는 기본 포트 |

`docker ps` 에서 두 컨테이너(포스트그레/레디스)가 모두 `Up` 상태인지 확인합니다.

```bash
docker ps
# CONTAINER ID   IMAGE                    ...   PORTS
# ...            postgis/postgis:15-3.4   ...   0.0.0.0:5432->5432/tcp
# ...            redis:7-alpine           ...   0.0.0.0:6379->6379/tcp
```

---

## 4. 애플리케이션 테스트 실행

### 4.1 기본(H2) 환경 테스트

프로젝트 기본 프로필은 `local`이며, H2 인메모리 DB를 사용합니다. 빠른 회귀 테스트는 다음 명령으로 수행합니다.

```bash
./gradlew test
```

실행이 끝나면 `BUILD SUCCESSFUL` 로그와 함께 `build/reports/tests/test/index.html` 에 상세 리포트가 생성됩니다.

### 4.2 Postgres 연결 테스트 (선택)

통합 테스트를 실제 Postgres에 붙여 실행하려면 아래와 같이 데이터소스 환경 변수를 지정할 수 있습니다.

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/matjom_db \
SPRING_DATASOURCE_USERNAME=matjom \
SPRING_DATASOURCE_PASSWORD=matjom \
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver \
SPRING_JPA_HIBERNATE_DDL_AUTO=none \
./gradlew test
```

> **주의:** 테스트에 필요한 최소 데이터(users, places, visits 등)가 존재하지 않으면 FK 제약으로 인해 실패할 수 있습니다. Postgres 기반 통합 테스트를 하려면 사전에 더미 데이터를 삽입하는 스크립트를 준비하세요.

### 4.3 Postgres 전용 시드 적용 및 재실행 결과

1. **시드 스크립트 적용** – `sql/postgres-test-seed.sql`

```bash
cat sql/postgres-test-seed.sql | \
  docker exec -i matjom-postgres psql -U matjom -d matjom_db
```

   - 사용자(UUID `1111...1111`), 장소 2건(IDs 1, 2), 방문 2건(IDs 10, 20)이 생성됩니다.
   - 스크립트는 `ON CONFLICT DO NOTHING` 을 사용하므로 반복 실행해도 안전합니다.

2. **Postgres 연결 테스트 재실행**

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/matjom_db \
SPRING_DATASOURCE_USERNAME=matjom \
SPRING_DATASOURCE_PASSWORD=matjom \
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver \
SPRING_JPA_HIBERNATE_DDL_AUTO=none \
./gradlew test
```

   - 결과: `BUILD SUCCESSFUL` (경고는 `@MockBean` 관련 Deprecation)
   - 이전에 발생했던 FK 제약 오류가 사라졌는지 확인합니다.

> **테스트 변경 사항**: 통합 테스트(`ReviewServiceIntegrationTest`, `LikeServiceIntegrationTest`)는 고정 UUID를 사용하고, 응답의 사용자/장소 이름이 비어 있지 않은지만 검증하도록 업데이트되었습니다. 덕분에 H2/포스트그레 모두 동일 테스트 코드를 사용합니다.

---

## 5. Postman/Curl 로 기능 수동 검증 (선택)

1. 애플리케이션을 로컬 실행 (`./gradlew bootRun` 혹은 IDE) 후 JWT 토큰을 확보합니다.
2. 다음 API를 순서대로 호출해 동작을 확인합니다.
   - `POST /api/v1/reviews` – 리뷰 작성
   - `POST /api/v1/likes` – 좋아요 등록
   - `POST /api/v1/reviews/{reviewId}/reports` – 리뷰 신고
   - `GET /api/places/{placeId}/stats` – 통계 조회 (Redis 캐시 히트 여부 로그 확인)

> **TIP:** 통계 API는 현재 직접 DB에서 스냅샷을 계산합니다. 자정 이후 값이 갱신되니, 테스트 시 날짜와 시간대를 함께 확인하세요.

### 5.1 Postgres/Redis 조합으로 애플리케이션 기동

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/matjom_db \
SPRING_DATASOURCE_USERNAME=matjom \
SPRING_DATASOURCE_PASSWORD=matjom \
SPRING_REDIS_HOST=localhost \
SPRING_REDIS_PORT=6379 \
./gradlew bootRun
```

- 서버가 기동한 뒤에는 JWT가 필요한 엔드포인트이므로, 테스트 계정용 토큰을 발급받아 Postman/curl에서 사용합니다.
- 추천 시나리오
  1. **리뷰 작성/수정/삭제** – `visitId=10`, `placeId=1`, `userId=1111...1111` 활용
  2. **좋아요 등록/취소/재활성화** – `visitId=20`, `placeId=2`
  3. **리뷰 신고** – 방금 작성한 리뷰 ID를 사용하여 신고 API 호출
  4. **통계 조회** – `GET /api/places/1/stats` 호출 후 Redis 캐시 히트 로그 확인

> **검증 팁:** Redis CLI (`docker exec -it matjom-redis redis-cli`)에서 `TTL place:stats:1` 명령을 사용하면 캐시가 저장되었는지 확인할 수 있습니다.

---

## 6. 컨테이너 정리

작업이 끝나면 컨테이너를 중지/삭제하여 리소스를 회수합니다.

```bash
docker rm -f matjom-postgres matjom-redis
```

필요 시 볼륨(`redis-data` 등)을 함께 삭제해 데이터를 초기화합니다.

---

## 7. 흔히 겪는 오류와 해결책

| 증상 | 원인 | 해결 |
| ---- | ---- | ---- |
| `type "geometry" does not exist` | 일반 postgres 이미지 사용 | `postgis/postgis` 이미지로 재실행 |
| 테스트에서 `ConstraintViolationException` | Postgres에 참조 데이터 없음 | 테스트 전 더미 데이터 INSERT 또는 H2 프로필 사용 |
| Redis 연결 실패 | 컨테이너 미실행 / 포트 충돌 | `docker ps` 확인, 필요한 경우 다른 포트 사용 |
| `command timed out` (Docker pull) | 이미지 다운로드 지연 | 네트워크 상태 확인, 재시도 |

---

## 8. 체크리스트 요약

- [ ] Docker, Java, Gradle 버전 확인
- [ ] `matjom-postgres` 컨테이너 실행 및 스키마 적용
- [ ] `matjom-redis` 컨테이너 실행
- [ ] `./gradlew test` (기본 H2) 통과
- [ ] 필요 시 Postgres 연결 테스트 및 데이터 준비
- [ ] 수동 API 점검 (리뷰/좋아요/신고/통계)
- [ ] 컨테이너 정리 (`docker rm -f ...`)

위 단계를 순차적으로 수행하면 처음 프로젝트를 받는 개발자도 동일 환경을 구축하고 테스트를 재현할 수 있습니다. 변경사항이 생길 경우 본 핸드북을 함께 업데이트하세요.
