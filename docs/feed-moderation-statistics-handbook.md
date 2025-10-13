# MatJom Core Domain Handbook  
**Feed · Moderation · Statistics**

> _작성일: 2025-09-30_  
> _대상 독자: MatJom 백엔드 신입 개발자, 코드 리뷰어, 운영 담당자_

본 핸드북은 MatJom 프로젝트의 세 축인 **Feed(리뷰·좋아요)**, **Moderation(신고·금칙어)**, **Statistics(장소 통계)** 패키지를 깊이 있게 이해하고 유지보수하기 위한 기술 문서입니다. 단순 API 명세를 넘어, 각 패키지가 해결하려는 문제, 연관 테이블/서비스, 내부에서 일어나는 주요 알고리즘, 그리고 서로 어떻게 상호 작용하는지까지 상세히 서술합니다.

문서는 크게 다음과 같은 질문에 답합니다.

1. 각 패키지는 어떤 문제를 해결하는가? (도메인 책임)
2. 요청 하나가 들어오면 컨트롤러 → 서비스 → 리포지토리 → DTO로 어떤 작업이 수행되는가? (로직 흐름)
3. 어떤 코드가 핵심을 담당하며, 그 코드가 왜 그런 방식으로 작성되었는가? (구현 의도와 근거)
4. 패키지 간 데이터는 어떻게 교차하며, 변경 시 어떤 영향이 있는가? (상호 의존성)
5. 프런트엔드에는 어떤 데이터를 어떤 형태로 돌려주며, 에러는 어떻게 처리되는가? (계약 및 예외)

---

## 1. 공통 토대와 시스템 가정

### 1.1 사용자 식별과 인증 흐름

- 모든 HTTP API는 JWT 기반 인증을 사용합니다. 스프링 시큐리티 필터가 토큰을 검증한 후 `CustomUserDetails` 객체를 생성하여 `SecurityContext`에 저장합니다.
- 컨트롤러는 `@RequestAttribute("userId")`로 인증된 사용자 UUID를 전달받습니다. 이 값은 서비스 계층에서 도메인 로직을 처리할 때 일관되게 사용됩니다.
- 이 구조 덕분에 서비스/리포지토리는 시큐리티 프레임워크에 의존하지 않고 순수한 UUID만 다루므로, 테스트 시에도 별도 시큐리티 설정 없이 Mock UUID를 주입할 수 있습니다.

### 1.2 BaseEntity와 소프트 삭제 정책

- `Review`, `DailyLike`, `ReviewReport` 등 핵심 테이블은 `BaseEntity`를 상속해 `createdAt`, `updatedAt`, `deletedAt` 필드를 공유합니다.
- 물리 삭제 대신 `deletedAt`을 채우는 방식으로 논리 삭제를 수행합니다. 엔티티에서는 `isDeleted()` 또는 `isActive()` 헬퍼를 두어 표현을 단순화합니다.
- 통계/신고에서 “현재 유효한 리뷰인지” 판별할 때는 `deletedAt IS NULL` 조건을 사용합니다. 코드에서도 이를 반영하기 위해 `ReviewRepository.existsByIdAndDeletedAtIsNull` 메서드를 제공합니다.

### 1.3 Clock 주입과 시간대 전략

- `src/main/java/com/matjom/matjom/common/config/TimeConfig.java`는 전역적으로 공유하는 `Clock.systemUTC()` 빈을 정의합니다.
- 통계/배치/모더레이션에서 모든 시간 계산은 이 Clock을 주입받아 처리합니다. 이를 통해 테스트에서 고정 시계를 주입하거나, 다중 인스턴스 환경에서 동일한 기준 시각을 사용하도록 보장합니다.
- 통계에서 KST(Asia/Seoul)를 기준으로 일자를 계산해야 할 경우 `OffsetDateTime.now(clock).atZoneSameInstant(KST)` 형태로 변환합니다.

---

## 2. Feed 패키지 – 리뷰 & 좋아요 도메인

### 2.1 도메인 개요

Feed 패키지는 사용자가 음식점 방문 기록(`visit`)을 바탕으로 남기는 **리뷰**와 **좋아요** 기능을 담당합니다. 주요 목표는 다음과 같습니다.

1. _방문 당 1회_라는 제약 하에서 리뷰/좋아요를 생성·수정·삭제(혹은 재활성화) 할 수 있도록 한다.
2. 최소한의 검증만 수행해 도메인을 단순하게 유지하되, 핵심 UX를 보호한다 (방문 여부 확인, 금칙어 차단 등).
3. 응답에서는 작성자 이름과 본문·작성 시각만 제공해 프런트가 필요한 최소 정보로 UI를 구성한다.

### 2.2 주요 구성요소 맵

| 레이어 | 클래스 | 역할 |
| ------ | ------ | ---- |
| Controller | `ReviewController`, `LikeController`, `VisitController` | HTTP 요청 바인딩, 사용자 UUID 추출, 서비스 호출 |
| Service | `ReviewService`, `LikeService`, `VisitHistoryService` | 비즈니스 로직, 검증, 엔티티 조립 |
| Helper | `VisitEligibilityChecker` | 리뷰/좋아요 공통 방침(ARRIVED 여부) 재사용 |
| Repository | `ReviewRepository`, `LikeRepository`, `VisitReadRepository`, `UserReadRepository` | 데이터 접근 |
| DTO | `ReviewResponseDTO`, `ReviewCreateRequestDTO`, `LikeCreateRequestDTO`, `LikeStatusResponseDTO`, `VisitCardResponseDTO` 외 | 컨트롤러 ↔ 서비스 ↔ 프런트 데이터 계약 |

### 2.3 방문 자격 검증 – “ARRIVED” 여부만 본다

리뷰와 좋아요는 모두 방문이 실제 완료되었을 때만 허용됩니다. 이를 위해 두 서비스는 공통 헬퍼 `VisitEligibilityChecker`를 의존합니다.

```java
// src/main/java/com/matjom/matjom/visit/service/VisitEligibilityChecker.java
@Component
@RequiredArgsConstructor
public class VisitEligibilityChecker {

    private final VisitReadRepository visitReadRepository;

    /**
     * 특정 사용자(userId)가 특정 방문(visitId)에 대해 ARRIVED 상태인지 확인한다.
     * 엔티티 전체를 불러오지 않고 Boolean 쿼리만 수행해 성능을 최적화한다.
     */
    public boolean isArrived(UUID userId, Long visitId) {
        return visitReadRepository.existsByIdAndUserIdAndState(visitId, userId, VisitState.ARRIVED);
    }

    /**
     * visitId가 생략된 경우 최신 ARRIVED 방문을 찾아 ID를 반환한다.
     */
    public Optional<Long> findLatestArrivedVisitId(UUID userId, Long placeId) {
        return visitReadRepository.findLatestArrivedVisitId(userId, placeId);
    }
}
```

리포지토리 구현은 아래와 같이 `SELECT CASE WHEN COUNT > 0` 쿼리를 사용합니다. 이렇게 하면 JPA가 `Visit` 엔티티의 다른 필드(좌표, 메타 JSON 등)를 전혀 로딩하지 않아도 되므로 최소 비용으로 자격을 판정할 수 있습니다.

```java
// src/main/java/com/matjom/matjom/visit/repository/VisitReadRepository.java
@Query("""
        SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END
        FROM Visit v
        WHERE v.id = :visitId
          AND v.user.id = :userId
          AND v.state = :state
          AND v.deletedAt IS NULL
    """)
boolean existsByIdAndUserIdAndState(@Param("visitId") Long visitId,
                                    @Param("userId") UUID userId,
                                    @Param("state") VisitState state);

Optional<Visit> findFirstByUser_IdAndPlace_IdAndStateAndDeletedAtIsNullOrderByArrivedAtDesc(UUID userId,
                                                                                           Long placeId,
                                                                                           VisitState state);

default Optional<Long> findLatestArrivedVisitId(UUID userId, Long placeId) {
    return findFirstByUser_IdAndPlace_IdAndStateAndDeletedAtIsNullOrderByArrivedAtDesc(userId, placeId, VisitState.ARRIVED)
            .map(Visit::getId);
}
```

### 2.4 리뷰 라이프사이클 상세

#### 2.4.1 요청 흐름

1. `POST /api/v1/reviews`
2. `ReviewController`가 `ReviewCreateRequestDTO(placeId, visitId?, text)`를 바인딩하고 `ReviewService.createReview`를 호출합니다.
3. `ReviewService`는 다음 순서로 검증 및 저장을 수행합니다.
   - (1) visitId가 전달되면 ARRIVED 여부를 검증하고, 없으면 `visitReadRepository.findLatestArrivedVisitId`로 최신 도착 방문을 선택
   - (2) 동일 방문에 이미 리뷰가 있는지 검사 (`ReviewRepository.existsByVisitId`)
   - (3) 금칙어 검증 (`profanityFilter.validate`)
   - (4) 엔티티 생성 후 저장, 응답 DTO 조립

아래 코드는 실제 저장 로직의 핵심 부분입니다.

```java
// src/main/java/com/matjom/matjom/feed/service/ReviewService.java
Review savedReview = reviewRepository.save(Review.builder()
        .userId(userId)
        .placeId(request.getPlaceId())
        .visitId(request.getVisitId())
        .text(request.getText())
        .build());

return reviewResponseAssembler.toDto(savedReview);
```

#### 2.4.2 응답 조립 – 이름 조회와 오류 내성

리뷰 응답은 `ReviewResponseDTO`를 사용하며, **작성자 이름·본문·작성 시각**만 포함합니다. 어셈블러는 사용자 이름 조회만 수행하고, 실패 시 기본 문자열 "알 수 없음"을 반환해 API 전체가 실패하지 않도록 구성했습니다.

```java
// src/main/java/com/matjom/matjom/feed/dto/assembler/ReviewResponseAssembler.java
private String loadReviewerName(UUID userId) {
    try {
        return userReadRepository.findNameById(userId).orElse(UNKNOWN);
    } catch (DataAccessException ex) {
        log.debug("사용자 이름 조회 실패: userId={}", userId, ex);
        return UNKNOWN;
    }
}
```

> **주의:** 사용자 이름 조회는 읽기 전용 보조 쿼리입니다. 서비스 장애를 막기 위해 예외를 잡아 삼키고 기본값을 제공하지만, 로그에는 디버깅용 정보가 남습니다.

### 2.5 좋아요 라이프사이클 상세

좋아요 흐름은 리뷰와 거의 동일하되, 상태가 `ACTIVE ↔ CANCELLED` 로 전환된다는 점이 다릅니다. 응답은 공통 포맷 `ApiResponse.ok()`만 사용하여 성공 여부만 프런트에 알립니다.

- `createLike`: 방문 자격·중복 검사 후 엔티티 저장, 응답은 `ApiResponse.ok()`
- `cancelLike`: 해당 사용자의 `likeId`를 찾아 상태를 `CANCELLED`로 전환, 취소 시각 기록
- `reactivateLike`: `CANCELLED` 상태인지 확인 후 `ACTIVE`로 복구, 응답은 `ApiResponse.ok()`

```java
// src/main/java/com/matjom/matjom/feed/entity/likes/DailyLike.java
public void cancel(OffsetDateTime cancelledAt) {
    this.status = LikeStatus.CANCELLED;
    this.cancelledAt = cancelledAt;
}

public void reactivate() {
    this.status = LikeStatus.ACTIVE;
    this.cancelledAt = null;
}
```

### 2.6 오류 처리와 예외 코드

Feed 도메인은 `FeedException`을 사용하며, `ErrorCode` Enum에 사유를 정의합니다.

| 상황 | ErrorCode | HTTP 기본값 |
| ---- | --------- | ------------ |
| ARRIVED 전 작성 | `REVIEW_NOT_ALLOWED` / `LIKE_NOT_ALLOWED` | 400 |
| 중복 작성/좋아요 | `REVIEW_ALREADY_EXISTS` / `LIKE_ALREADY_EXISTS` | 400 |
| 타인 리뷰 수정·삭제 | `FORBIDDEN` | 403 |
| 대상 없음 | `REVIEW_NOT_FOUND` 등 | 404 |

컨트롤러 레이어는 `ApiResponse.ok()` 또는 예외를 던지는 방식으로만 응답하므로, 공통 에러 처리기가 HTTP 상태와 메시지를 생성합니다.

### 2.7 API 계약 일람 (Feed)

| 엔드포인트 | 요청 DTO | 주요 요청 필드 | 응답 DTO | 주요 응답 필드 |
| ----------- | --------- | -------------- | -------- | ---------------- |
| `POST /api/v1/reviews` | `ReviewCreateRequestDTO` | `placeId`, `visitId?`, `text` | `ReviewResponseDTO` | `reviewerName`, `text`, `createdAt` |
| `PUT /api/v1/reviews/{reviewId}` | `ReviewUpdateRequestDTO` | `text` | `ReviewResponseDTO` | `reviewerName`, `text`, `createdAt` |
| `DELETE /api/v1/reviews/{reviewId}` | - | 경로 변수 `reviewId` | `ApiResponse<Void>` | 성공 여부만 반환 |
| `GET /api/v1/reviews/my` | - | JWT 사용자 | `List<ReviewResponseDTO>` | 사용자 리뷰 목록 |
| `GET /api/v1/reviews?placeId=` | - | `placeId` 쿼리 파라미터 | `List<ReviewResponseDTO>` | 장소 리뷰 목록 |
| `POST /api/v1/likes` | `DailyLikeCreateRequestDTO` | `placeId`, `visitId?` | `ApiResponse<Void>` | 성공 여부만 반환 |
| `DELETE /api/v1/likes/{likeId}` | - | 경로 변수 `likeId` | `ApiResponse<Void>` | 성공 여부만 반환 |
| `PUT /api/v1/likes/{likeId}` | - | 경로 변수 `likeId` | `ApiResponse<Void>` | 성공 여부만 반환 |

> **메모:** 좋아요 API는 `ApiResponse.ok()`만 반환해 “성공” 여부만 전달하며, 상태 토글은 프런트에서 별도 호출(재조회)로 확인한다.

### 2.9 도착 방문 자동 매칭 전략

프런트엔드가 “장소 상세 페이지에서 버튼을 누르면 즉시 리뷰/좋아요를 등록한다”는 UX를 제공하려면, 백엔드가 사용자의 최신 도착 방문(`Visit.state = ARRIVED`)을 자동으로 찾아 주어야 합니다. 이를 위해 다음과 같이 API 계약과 서비스 로직을 보완했습니다.

1. **요청 DTO 선택 항목화**
   - `ReviewCreateRequestDTO`, `DailyLikeCreateRequestDTO`의 `visitId`는 *선택* 필드입니다. 기본적으로 프런트는 `placeId`와 본문(리뷰)만 전달하고, 서버가 자동으로 ARRIVED 방문을 찾습니다.
   - 예외적으로 여러 방문이 동시에 ARRIVED 상태로 남아 있고 클라이언트가 명시적으로 특정 방문을 지정하고 싶다면, `visitId`를 채워 보내면 그 값을 그대로 사용합니다.

2. **서비스 계층에서 최신 ARRIVED 방문 조회**
   ```java
   // ReviewService#createReview 요약 (9월 30일 최종)
   Long resolvedVisitId = resolveArrivedVisitId(userId, request.getPlaceId(), request.getVisitId());
   ```
   - 내부에서는 `visitEligibilityChecker.findLatestArrivedVisitId`로 `userId`/`placeId`에 맞는 최신 ARRIVED 방문을 조회합니다.
   - 좋아요 서비스에서도 동일한 패턴을 사용해 로직을 공유합니다.

3. **좋아요 토글 시나리오 정리**
   - 프런트는 최초 화면 진입 시 별도 캐시/Facade 응답으로 현재 좋아요 여부를 보관하고, 버튼 클릭 후에는 재조회(refetch)로 최신 상태를 반영합니다.
   - 버튼 클릭 시 `POST /api/v1/likes`로 등록하거나, 활성 상태일 경우 `DELETE /api/v1/likes/{likeId}`로 취소합니다.
   - 서버는 내부적으로 최신 ARRIVED 방문을 찾아 `DailyLike`를 생성/취소/재활성화합니다.

4. **보조 방문 조회 API (선택)**
   - 예외 상황 대비 용도로 `GET /api/v1/visits/my?placeId={id}&state=ARRIVED&limit=5` 등 “최근 도착 방문 목록”을 제공할 수 있습니다.
   - 기본 UX에서는 자동 매칭만으로 충분하지만, 운영자가 특정 방문을 지정해 재처리해야 할 때 활용할 수 있습니다.

5. **에러 처리 정책**
   - 자동 매칭에서 ARRIVED 방문을 찾지 못하면 `FeedException(REVIEW_NOT_ALLOWED 또는 LIKE_NOT_ALLOWED)`을 던지며 400 응답을 반환합니다. 프런트는 “도착한 방문이 없습니다” 등의 고정 문구를 노출합니다.
   - 중복 작성/좋아요 등 기존 예외 코드는 그대로 유지됩니다.

이 설계를 통해 프런트는 **placeId만으로** 리뷰/좋아요를 전송해도 되고, 사용자는 장소 상세 페이지에서 별도 선택 없이 즉시 행동할 수 있습니다. 동시에 백엔드는 ARRIVED 상태를 강제함으로써 비즈니스 규칙(방문 완료 이후에만 리뷰/좋아요 허용)을 계속 보장합니다.

#### 2.9.1 프런트 좋아요 구현 예시

자동 매칭 로직에 맞춰 프런트에서는 “상태 보관 → API 호출 → 재조회(refetch)” 구조로 구현합니다. 아래 예시는 React + React Query 조합입니다.

```tsx
// hooks/usePlaceDetail.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';

export function usePlaceDetail(placeId: number) {
  return useQuery(['placeDetail', placeId], async () => {
    const { data } = await axios.get(`/api/places/${placeId}/detail`);
    return data.data; // { statistics, reviews, totalReviewCount, liked, likeId, ... }
  });
}

async function postLike(placeId: number, visitId?: number) {
  await axios.post('/api/v1/likes', { placeId, visitId });
}

async function deleteLike(likeId: string) {
  await axios.delete(`/api/v1/likes/${likeId}`);
}

export function useLikeMutations(placeId: number, visitId?: number) {
  const qc = useQueryClient();

  const likeMutation = useMutation(() => postLike(placeId, visitId), {
    onSuccess: () => qc.invalidateQueries(['placeDetail', placeId]),
  });

  const unlikeMutation = useMutation((likeId: string) => deleteLike(likeId), {
    onSuccess: () => qc.invalidateQueries(['placeDetail', placeId]),
  });

  return { likeMutation, unlikeMutation };
}
```

```tsx
// components/PlaceDetailPage.tsx
import { usePlaceDetail, useLikeMutations } from '../hooks/usePlaceDetail';

export default function PlaceDetailPage({ placeId, visitId }: { placeId: number; visitId?: number }) {
  const { data, isLoading } = usePlaceDetail(placeId);
  const { likeMutation, unlikeMutation } = useLikeMutations(placeId, visitId);

  if (isLoading || !data) return <div>로딩 중…</div>;

  const handleToggleLike = () => {
    if (data.liked && data.likeId) {
      unlikeMutation.mutate(data.likeId);
    } else {
      likeMutation.mutate();
    }
  };

  return (
    <section>
      <header>…</header>
      <div>
        <button onClick={handleToggleLike} disabled={likeMutation.isLoading || unlikeMutation.isLoading}>
          {data.liked ? '👍 좋아요 취소' : '👍 좋아요'}
        </button>
      </div>
      <StatsPanel stats={data.statistics} />
      <ReviewList reviews={data.reviews} />
    </section>
  );
}
```

핵심 포인트

- `visitId`는 도착한 방문이 명확할 때 넘기고, 생략하면 서버가 최신 ARRIVED 방문을 자동 선택합니다.
- 성공 응답은 `ApiResponse.ok()` 하나뿐이므로, `invalidateQueries` 또는 `refetch`로 최신 상태를 다시 불러와 아이콘을 토글합니다.
- 취소/재활성화 시 필요한 `likeId`는 Place Detail 응답에 포함해두고, 버튼 클릭 시 활용합니다.
- 실패(`FeedException`)는 `useMutation`의 `onError`에서 토스트 등으로 안내합니다.

### 2.8 테스트 전략

- `ReviewServiceTest`, `DailyLikeServiceTest`: Mockito 기반 단위 테스트. 자격 미충족/중복/정상 케이스를 커버합니다.
- `ReviewServiceIntegrationTest`, `DailyLikeServiceIntegrationTest`: `@SpringBootTest`로 실제 리포지토리를 띄워 CRUD가 정상 동작하는지 확인합니다. 방문 자격은 `@MockBean`으로 대체하여 도메인 핵심에 집중합니다.

테스트 메소드에는 "무엇을 테스트하는지, 어떤 상황인지, 기대 결과"를 한글 주석으로 명시해 향후 유지보수자가 쉽게 이해할 수 있습니다 (예: `src/test/java/com/matjom/matjom/feed/service/ReviewServiceIntegrationTest.java:43`).

---

## 3. Moderation 패키지 – 신고와 금칙어

### 3.1 도메인 개요

Moderation 패키지는 두 가지 책임을 가집니다.

1. 리뷰 작성/수정 시 금칙어를 탐지해 저장을 차단한다.
2. 사용자가 리뷰를 신고할 수 있게 하고, 신고 이력과 누적 건수를 저장한다.

자동 제재(리뷰 삭제, 숨김) 등은 싱글 책임 원칙에 따라 포함하지 않았습니다. 신고 건수는 통계/운영 툴에서 활용할 수 있도록 저장만 합니다.

### 3.2 금칙어 필터

`ProfanityFilter` 인터페이스는 `validate(String text)` 한 메서드만 제공합니다. 구현체인 `HardcodedProfanityFilter`는 간단한 블랙리스트를 사용합니다.

```java
// src/main/java/com/matjom/matjom/moderation/profanity/HardcodedProfanityFilter.java
private final Set<String> blacklist = Set.of("욕설1", "욕설2", "비속어3");

private boolean hasBlacklistedWord(String text) {
    if (text == null || text.isBlank()) {
        return false;
    }
    String normalized = text.toLowerCase().replaceAll("[^가-힣a-z0-9]", "");
    return blacklist.stream().anyMatch(normalized::contains);
}
```

- 리뷰 작성 시 `ReviewService`가 `profanityFilter.validate(request.getText())`를 호출합니다.
- 금칙어가 발견되면 `FeedException(ErrorCode.REVIEW_BAD_LANGUAGE)`가 발생하고 API는 400으로 응답합니다.
- 향후 확장(외부 필터 연동 등)을 고려해 인터페이스 기반으로 설계되었습니다.

### 3.3 리뷰 신고 세부 흐름

1. **중복 신고 차단**: `ReviewReportRepository.existsByReviewIdAndReporterId`로 동일 사용자의 중복 신고를 막습니다.
2. **리뷰 존재 확인**: `ReviewRepository.existsByIdAndDeletedAtIsNull`을 호출해 삭제된 리뷰가 아닌지 확인합니다.
3. **신고 저장**: `ReviewReport` 엔티티를 생성해 신고 사유/설명을 저장합니다.
4. **누적 3회 이상이면 자동 삭제**: `ReviewRepository.findById`로 리뷰를 불러와 `markDeleted()` 처리합니다.
5. **응답**: 성공 여부만 돌려주고, 프런트는 “신고가 접수되었습니다.” 같은 고정 문구를 자체적으로 보여주면 됩니다.

```java
// src/main/java/com/matjom/matjom/moderation/report/service/ReviewModerationService.java
ReviewReport saved = reviewReportRepository.save(...);
long reportCount = reviewReportRepository.countByReviewId(reviewId);

if (reportCount >= 3) {
    reviewRepository.findById(reviewId)
            .filter(review -> !review.isDeleted())
            .ifPresent(review -> {
                review.markDeleted();
                log.info("리뷰 자동 삭제 처리: reviewId={}, reportCount={}", reviewId, reportCount);
            });
}

log.info("리뷰 신고 기록 생성: reviewId={}, reporterId={}, reportId={}, reportCount={}",
        reviewId, reporterId, saved.getId(), reportCount);

// 컨트롤러에서는 ApiResponse.ok()만 반환해 성공 여부만 전달한다.
```

```tsx
// 예시: 프론트(React)에서 Axios와 토스트 컴포넌트를 사용해 신고 완료 메시지 출력
import axios from 'axios';
import { toast } from '@/components/ui/toast';

async function handleReport(reviewId: string, payload: { reason: string; description?: string }) {
  try {
    const response = await axios.post<ApiResponse<void>>(
      `/api/v1/reviews/${reviewId}/reports`,
      payload,
      { headers: { Authorization: `Bearer ${token}` } }
    );

    if (response.data.success) {
      toast.success('신고가 접수되었습니다.');
    } else {
      toast.error(response.data.error?.message ?? '신고 처리 중 문제가 발생했습니다.');
    }
  } catch (error) {
    toast.error('네트워크 오류로 신고에 실패했습니다.');
  }
}
```

### 3.4 데이터 스키마와 엔티티

- `review_reports` 테이블은 `review_id`, `reporter_id`, `reason`, `description`을 갖습니다. `reporter_id`는 `users(id)`를 참조합니다.
- 엔티티(`ReviewReport`) 역시 UUID 기반으로 FK를 저장하므로, 신고와 사용자/리뷰 간 연결이 명확합니다.

### 3.5 API 계약 (프런트 가이드)

- 요청: `POST /api/v1/reviews/{reviewId}/reports`
- 요청 바디: `reason`(필수 ENUM), `description`(선택)
- 응답: 본문 없이 성공 여부만 반환 (`ApiResponse.ok()`)
- 프런트는 “신고가 접수되었습니다.” 같은 문구를 자체적으로 띄운다.

### 3.6 테스트 전략

`ReviewModerationServiceTest`는 다음 세 가지 핵심 시나리오를 검증합니다.

1. 중복 신고 시 `REVIEW_REPORT_ALREADY_EXISTS`
2. 존재하지 않는 리뷰 신고 시 `REVIEW_NOT_FOUND`
3. 정상 신고 시 DB에 신고 이력이 저장되고, `ReviewService`와 `ReviewRepository`를 통해 누적 신고 수 3회 이상이면 자동으로 소프트 삭제된다.

### 3.7 Feed와의 상호 작용

- **금칙어 검증**: 리뷰 작성/수정 시 Moderation 패키지의 `ProfanityFilter`를 활용합니다.
- **리뷰 존재 여부 확인**: 신고 시 Feed의 `ReviewRepository`를 참조합니다.
- **신고 데이터 활용**: 현재는 통계 패키지에서 직접 사용하진 않지만, 향후 신고율 등의 메트릭을 위해 `ReviewReport` 데이터를 참조할 수 있습니다.

---

## 4. Statistics 패키지 – 장소 통계

### 4.1 목표와 범위

Statistics 패키지는 사용자에게 음식점의 전반적인 인기와 혼잡도를 판단할 수 있는 정보를 제공합니다. 주요 지표는 다음과 같습니다.

- `totalVisitors`: 누적 방문자 수 (`visits.arrived_at` 기준)
- `totalLikes`: 누적 좋아요 수 (`daily_likes.status = 'ACTIVE'`)
- `hourlyArrivals`: 11시부터 20시까지 시간대별 최근 14일 평균 도착 인원 배열

모든 지표는 매 요청 시 DB 스냅샷으로 계산하며 별도 캐시·배치 과정은 존재하지 않습니다.

### 4.2 구성요소 지도

| 레이어 | 클래스 | 설명 |
| ------ | ------ | ---- |
| Controller | `StatisticsController` | `/api/places/{placeId}/stats` 응답 |
| Service | `StatisticsService` | 장소 존재 검증 + 통계 스냅샷 조합 |
| Repository | `StatisticsRepository` | 누적/시간대별 통계 네이티브 쿼리 |
| DTO | `StatsResponseDTO`, `StatsSnapshot` | 응답 구조, 변환 전용 스냅샷 |

### 4.3 실시간 통계 조회 시퀀스

1. **컨트롤러 호출**: `GET /api/places/{placeId}/stats`
2. **서비스 호출**: `StatisticsService.fetchStats(placeId)`
   - (a) `PlaceReadRepository.findNameById(placeId)`로 장소 존재 여부 및 이름 확보
   - (b) `StatisticsRepository.fetchSnapshot(placeId, 현재일)`로 DB 스냅샷 조회
   - (c) `StatsResponseDTO.of(...)`로 DTO 작성
3. **응답 반환**: 최종 DTO를 프런트에 전달

### 4.4 StatsSnapshot과 DB 쿼리

- `StatisticsRepository.fetchSnapshot`은 방문(`visits`)·좋아요(`daily_likes`)에서 누적치를 계산하고, 11~20시 모든 시간대에 대해 최근 14일 평균 도착 인원을 직접 집계한다.
- 반환 타입 `StatsSnapshot`은 Storage 로직을 서비스에 노출하지 않고 DTO 변환만 담당한다.

```java
// src/main/java/com/matjom/matjom/statistics/dto/StatsSnapshot.java
public record StatsSnapshot(
        long totalVisitors,
        long totalLikes,
        Map<Integer, Long> hourlyArrivals
) {}
```

### 4.5 통계 응답 계약

```json
{
  "placeName": "맛좋은 식당",
  "totalVisitors": 1200,
  "totalLikes": 450,
  "hourlyArrivals": [
    { "hour": 11, "averageCount": 32 },
    { "hour": 12, "averageCount": 45 },
    { "hour": 13, "averageCount": 38 },
    { "hour": 14, "averageCount": 30 },
    { "hour": 15, "averageCount": 28 },
    { "hour": 16, "averageCount": 26 },
    { "hour": 17, "averageCount": 35 },
    { "hour": 18, "averageCount": 42 },
    { "hour": 19, "averageCount": 33 },
    { "hour": 20, "averageCount": 25 }
  ]
}
```

- 프런트는 응답 필드를 그대로 카드/그래프 등에 렌더링하면 됩니다.

### 4.7 API 계약 일람 (Statistics)

| 엔드포인트 | 요청 DTO | 주요 요청 필드 | 응답 DTO | 주요 응답 필드 |
| ----------- | --------- | -------------- | -------- | ---------------- |
| `GET /api/places/{placeId}/stats` | - | 경로 변수 `placeId` | `ApiResponse<StatsResponseDTO>` | `placeName`, `totalVisitors`, `totalLikes`, `hourlyArrivals` |

`StatsResponseDTO` 내부 필드 해석:

- `placeName`: 장소명 (프런트 표시용)
- `totalVisitors`: 누적 방문자 수
- `totalLikes`: 누적 좋아요 수
- `hourlyArrivals`: 11~20시 시간대별 최근 14일 평균 도착 인원 배열

### 4.8 에러 처리

- 존재하지 않는 장소 요청 시 `PlaceException(ErrorCode.PLACE_NOT_FOUND)`가 발생하며 404로 응답됩니다.
- 통계는 캐시를 사용하지 않으므로, DB 조회 에러만 주의하면 됩니다. 예외 발생 시 공통 예외 처리기가 적절한 에러 응답을 반환합니다.

### 4.9 테스트 전략

- `StatisticsServiceTest`: Clock 주입, 장소 미존재 예외, 스냅샷 변환을 단위 테스트합니다.
- `StatisticsControllerTest`: 컨트롤러가 응답 구조(`hourlyArrivals`)를 그대로 노출하는지 검증합니다.

---

## 5. 패키지 간 상호작용과 데이터 플로우

### 5.1 의존 관계 개요

```
Feed ──────▶ Moderation (금칙어 검증)
  │            ▲
  │            │
  ▼            └─ 신고 시 리뷰 존재 여부 확인
Statistics ──▶ Feed (장소명 조회, 리뷰/좋아요 집계)

```

- **Feed ↔ Moderation**: Feed는 `ProfanityFilter`를 사용해 텍스트를 검증하며, Moderation은 신고를 저장하기 전에 Feed의 `ReviewRepository`로 리뷰 존재를 확인합니다.
- **Feed ↔ Statistics**: 통계 조회 시 `PlaceReadRepository` 등 Feed의 리포지토리를 활용합니다. 반대로 통계 결과는 Feed 응답에는 직접 포함되지 않지만, 같은 장소 ID를 사용하므로 프런트에서 리뷰/통계를 함께 요청하면 UI에 시너지가 생깁니다.

### 5.2 시퀀스 예제 – “리뷰 작성 후 통계 조회”

1. **리뷰 작성** (`POST /reviews`)
   - Feed가 ARRIVED 여부, 금칙어, 중복 검사를 수행하고 리뷰 저장
   - 이벤트 발행 대신 통계는 "조회 시점 계산" 방법을 채택 (실시간 반영은 통계 쪽에서 DB 조회로 수행)
2. **통계 조회** (`GET /places/{id}/stats`)
   - 통계 레포지토리가 즉시 DB 스냅샷을 계산해 반환합니다.
   - 리뷰 수는 `reviews` 테이블에서 직접 계산되므로 방금 작성한 리뷰가 바로 반영됩니다.

### 5.3 시퀀스 예제 – “리뷰 신고 흐름”

1. 프런트가 특정 리뷰를 신고 (`POST /reviews/{reviewId}/reports`)
2. Moderation 서비스가 중복 신고 여부, 리뷰 존재 여부를 검사
3. 신고 이력을 저장하고 누적 신고 건수를 응답으로 반환
4. 통계/운영 시스템은 `review_reports` 테이블을 통해 신고 건수를 별도 분석할 수 있습니다.

---

## 6. 테스트와 품질 보증

- **단위 테스트**: 각 서비스는 핵심 분기(허용/거부)를 모두 커버하도록 작성했습니다. Mockito를 사용해 의존성(리포지토리, 필터 등)을 주입하고, 코드 상단에 목적/상황/기대를 주석으로 명시했습니다.
- **통합 테스트**: Feed 도메인은 실제 JPA 리포지토리를 사용해 CRUD를 검증합니다. Visit 자격은 MockBean으로 처리해 테스트 환경에서 Visit 데이터를 별도로 준비하지 않아도 됩니다.
- **통계 테스트**: 장소 미존재, 배치 집계 결과, DTO 변환을 중심으로 검증합니다.

---

## 7. 운영 및 향후 확장 가이드

1. **금칙어 관리**: 현재는 하드코딩된 블랙리스트를 사용합니다. 외부 서비스 연동이나 DB 기반 관리로 확장하려면 `ProfanityFilter` 구현체만 교체하면 됩니다.
2. **신고 후 조치**: 누적 신고 건수가 3건 이상 누적되면 서비스가 자동으로 리뷰를 삭제합니다. 필요하면 추가 알림 시스템과 연계해 운영자에게 전달할 수 있습니다.
3. **통계 확장**: `StatsSnapshot`에 새 필드를 추가하면 DTO와 프런트 코드를 함께 수정해야 합니다. 변경 시 본 핸드북과 `docs/uc-stat-01-api.md`, `statistics-task-plan.md`를 함께 업데이트하세요.

---

## 8. 추가 참고 문서

- `docs/review-report-api.md` – 리뷰 신고 API 상세 명세
- `docs/statistics-presentation.md` – 통계 아키텍처 슬라이드 요약
- `docs/statistics-change-log.md` – 통계 패키지 변경 이력
- `tasks/review-like-summary.md` – Feed/Moderation/Statistics 작업 로그 및 Q&A

---

## 9. 마무리

이 문서가 목표로 하는 바는 "코드를 직접 열어 확인하지 않아도 핵심 설계 의도를 이해"할 수 있도록 돕는 것입니다. 변경 사항이 생기면 반드시 해당 섹션을 갱신해 팀 전체가 최신 상태를 공유하세요. 특히 응답 DTO 필드나 예외 정책이 달라질 경우 프런트/QA/운영 문서까지 연쇄적으로 영향을 받습니다.

> **Checklist – 수정 시 같이 업데이트할 것**
> 1. 본 핸드북 (패키지 개요, 코드 스니펫)
> 2. 관련 API 문서 (`docs/review-report-api.md`, `docs/uc-stat-01-api.md` 등)
> 3. 작업 로그 (`tasks/review-like-summary.md`, `tasks/statistics-task-plan.md`)
> 4. 테스트 (단위·통합)와 기대 결과 주석

---

### 추가 메모 – VisitReadRepository 재사용 권장

**Role: 백엔드 아키텍트**

**전문가 협의**

- 도메인 설계 전문가: 이미 `VisitReadRepository`가 존재하며(`src/main/java/com/matjom/matjom/visit/repository/VisitReadRepository.java:10`), `existsByIdAndUserIdAndState` 하나로 ARRIVED 여부만 판정하도록 간결하게 짜여 있다. 새 저장소를 추가하기보다 이 구현을 그대로 활용·확장하는 편이 구조를 어지럽히지 않는다.
- 데이터 레이어 전문가: 만약 팀장님이 제공할 정식 Visit 리포지토리를 아직 쓸 수 없다면, 동일한 시그니처로 임시 저장소를 두어도 JPA가 알아서 프록시를 만들어 주니 추가 구현이 사실상 필요 없다. 엔티티 전부를 읽어 오지 않고 EXISTS 형태라 성능도 충분하다.
- QA 전문가: 현 구조를 유지하면 서비스·테스트가 이미 이 메서드 기반으로 정리돼 있어 회귀 위험이 없다. 새로운 저장소를 만들면 테스트와 문서를 다시 손봐야 하니 현재 구성을 유지하는 것이 안정적이다.

**추천**

지금은 `VisitReadRepository`를 바로 주입받아 쓰면 필요한 자격 검사(ARRIVED 확인)가 해결된다. 팀장님 쪽에서 정식 Visit 모듈을 전달받기 전까지도 추가 코드 없이 동작하니, 임시 저장소를 새로 만들 이유는 없다. 만약 이름만 혼란스럽다면, 같은 파일에 아래처럼 주석을 보강하는 정도로 정리해 두자.

```java
@Query("""
    SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END
    FROM Visit v
    WHERE v.id = :visitId
      AND v.user.id = :userId
      AND v.state = :state
      AND v.deletedAt IS NULL
""")
// 9월 30일 최종: ARRIVED 방문 존재 여부만 확인하는 경량 검증
boolean existsByIdAndUserIdAndState(@Param("visitId") Long visitId,
                                    @Param("userId") UUID userId,
                                    @Param("state") VisitState state);
```

이렇게 두면 리뷰·좋아요 서비스가 지금처럼 `visitEligibilityChecker.check(…)` → `exists…(ARRIVED)` 흐름을 그대로 유지할 수 있다.

## 부록 A. Redis 캐시를 사용하는 이유

Role: 데이터 아키텍트 관점 정리

1. **통계 계산은 비용이 크다.** 장소별 누적 방문자·좋아요·시간대 평균을 요청마다 DB에서 다시 계산하면 쿼리 부하가 커진다. 캐시에 한 번 저장해 두면 TTL 동안은 DB를 건너뛰고 곧바로 응답할 수 있다.
2. **인메모리 캐시는 확장성이 떨어진다.** 애플리케이션 힙에 캐시하면 인스턴스마다 따로 들고 있게 되고, 서버가 재시작되면 캐시가 사라진다. 또한 다중 인스턴스 환경에서는 어떤 서버는 캐시가 있고, 어떤 서버는 없어 DB를 또 조회하는 문제가 생긴다.
3. **Redis는 “외부에 둔 메모리 저장소”다.** 별도 서버(혹은 컨테이너)로 띄운 키-값 저장소이므로, 여러 애플리케이션 인스턴스가 같은 캐시를 공유할 수 있고, 서비스 재시작에도 캐시가 유지된다. TTL, 키 삭제, 다양한 자료구조 지원 등 제어도 용이하다.

결론적으로, 통계 API처럼 재계산 비용이 큰 데이터를 빠르게 제공하고, 여러 서버가 동일한 캐시를 쓰게 하려면 Redis 같은 공용 캐시가 필요하다. 현재는 하루에 한 번만 값이 변해 DB 조회만으로 충분하지만, 트래픽 증가나 실시간 갱신 요구가 생기면 Redis를 도입할 수 있도록 배경 지식을 정리해 둔다.

_이상으로 Feed · Moderation · Statistics의 현재 구조를 마스터했습니다. 신규 기능을 설계하거나 이슈를 디버깅할 때 본 문서를 시작점으로 삼으세요._
