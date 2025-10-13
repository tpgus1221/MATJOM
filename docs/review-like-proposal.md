# 리뷰 좋아요 기능 제안서 (팀장 검토용)

## 1. 개요
- 목적: 리뷰 카드마다 사용자가 "추천(좋아요)"을 표시할 수 있게 해 피드 상호작용을 강화.
- 현재 상태: `daily_likes`는 장소 방문 단위 좋아요만 지원, 리뷰에는 관련 스키마/로직 없음.
- 요구 사항: 리뷰 좋아요 수 집계, 사용자 한 번만 누를 수 있는 토글 동작, 삭제/신고 처리와의 정합성.

## 2. 도메인/DB 설계
- 테이블 신설: `review_likes`
  - 컬럼: `id (PK)`, `review_id (FK)`, `user_id (UUID FK)`, `status (ACTIVE/CANCELLED)`, `created_at`, `updated_at`
  - 제약: `UNIQUE(review_id, user_id)`로 중복 방지
  - 인덱스: `idx_review_likes_review_status`, `idx_review_likes_user`
- 엔티티: `ReviewLike`
- 리뷰 엔티티 업데이트: `likeCount`(Long) 필드 or 집계용 조회 쿼리
- 삭제된 리뷰: 조회 시 제외하거나, 리뷰 삭제 시 관련 좋아요 `status=CANCELLED` 처리

## 3. 리포지토리 & 쿼리
- `ReviewLikeRepository`
  - `boolean existsByReviewIdAndUserIdAndStatus(...)`
  - `Optional<ReviewLike> findByReviewIdAndUserId(...)`
  - `long countByReviewIdAndStatus(reviewId, ACTIVE)`
- 리뷰 목록 조회 시 `likeCount`, `likedByMe`를 포함하도록 JPQL/네이티브 쿼리 검토 (서브쿼리 or 조인)

## 4. 서비스 계층
- `ReviewLikeService` (신규)
  - `toggle(reviewId, userId)` → 리뷰 존재/ACTIVE 검증 → ACTIVE이면 CANCEL, 아니면 생성
  - 본인 리뷰 권한 정책(허용/금지) 결정
- `ReviewService`
  - 리뷰 조회 DTO에 좋아요 정보 채워 넣기 (`likeCount`, `likedByMe`)
- `ReviewModerationService`
  - 리뷰가 신고 3회로 삭제될 때 관련 좋아요 비활성화(선택) 또는 조회 시 필터링

## 5. 컨트롤러 & DTO
- 엔드포인트 초안
  - `POST /api/v1/reviews/{reviewId}/likes` (좋아요)
  - `DELETE /api/v1/reviews/{reviewId}/likes` (취소) 또는 `POST /toggle`
- 공통 응답: `ApiResponse<Void>`
- 에러 코드 추가: `REVIEW_LIKE_FORBIDDEN`, `REVIEW_NOT_FOUND`, `REVIEW_LIKE_ALREADY_EXISTS`
- `ReviewResponseDTO`
  - 필드 추가: `likeCount: long`, `likedByMe: boolean`

## 6. 테스트 전략
- 단위 테스트
  - `ReviewLikeServiceTest`: 토글 성공, 이미 눌렀을 때, 삭제 리뷰 예외, 본인 리뷰 케이스
- 통합 테스트
  - 리뷰 생성 → 좋아요 토글 → 조회 시 카운트 반영
  - 신고로 삭제된 리뷰가 좋아요 대상에서 제외되는지 확인
- 컨트롤러 MockMvc
  - 인증 유저 컨텍스트로 요청/응답 구조 검증

## 7. 문서 & 운영
- OpenAPI (`docs/openapi-feed-moderation-statistics.yaml`) 업데이트
- 운영 가이드(`docs/feed-moderation-statistics-handbook.md`, `tasks/review-like-summary.md`)에 흐름 추가
- DB 마이그레이션 스크립트 준비 (`sql/schema-postgres.sql` or Flyway)
- 통계 영향: 리뷰 좋아요 수를 향후 통계에 노출할지 여부 결정 후 `statistics` 패키지 연계 계획 수립

## 8. 진행 순서 체크리스트
1. DB 마이그레이션 스크립트 작성 및 적용
2. 엔티티/리포지토리 생성 → 서비스 토글 로직 구현
3. DTO/컨트롤러 확장 + 예외 코드 정의
4. 단위/통합 테스트 추가 및 실행
5. 문서/Swagger 업데이트
6. 프론트엔드 협업: UI 토글 버튼 + 토스트 메시지 + 카운트 노출
7. 리뷰 삭제/신고 시나리오에서 좋아요 상태 검증

## 9. 검토 포인트 (팀장 피드백 요청)
- 리뷰 좋아요가 필요하다고 판단되는 사용자 가치/핵심 지표 정의
- 본인 리뷰에 좋아요 허용 여부
- 삭제된 리뷰 좋아요 처리 방식 (하드 삭제 vs 논리적 비활성화)
- 통계/캐시 영향 범위 및 우선순위
