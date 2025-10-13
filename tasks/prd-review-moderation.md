# Review & Moderation Task Plan

팀장 피드백과 현재 코드 구조를 바탕으로 리뷰/좋아요 핵심 기능을 최소 정보로 유지하면서 구현·정리하기 위한 작업 계획입니다. 모든 API는 JWT 기반 `CustomUserDetails`에서 `userId`를 받아 사용하는 것을 전제로 하며, 방문 여부 검증은 `VisitRepository`를 통해 단순하게 처리합니다.

## Task List

- [x] Review/DTO 최소화
  - [x] `Review` 엔티티를 `ACTIVE/DELETED` 상태 중심으로 정리하고 불필요 필드(`flagged`, `warningCount`, `lastWarningAt` 등) 제거
  - [x] 리뷰 관련 DTO(Request/Response)와 컨트롤러 응답에서 필수 값(`reviewId`, `placeId`, `visitId`, `text`, `createdAt`)만 남기도록 정리
  - [x] `ReviewRepository` 쿼리 메서드를 실제 사용 중인 핵심 메서드만 남기고 정리

- [x] Visit 연동 단순 자격 검증
  - [x] `ReviewService.checkReviewEligibility`에서 `VisitRepository`를 사용해 `visitId` 존재, `arrived` 여부만 확인하도록 단순화
  - [x] 자격 미충족 시 던지는 `FeedException` 코드/메시지를 JWT 인증 흐름에 맞게 재검토
  - [x] 동일한 Check 로직을 좋아요 쪽(`LikeService` 24시간 자격 검증)에도 적용

- [x] 좋아요 플로우 단순화
  - [x] `Like` 엔티티/DTO에서 필요 없는 필드 제거, 상태 전환(`ACTIVE` ↔ `CANCELLED`)만 유지
  - [x] 좋아요 등록/취소/재활성화 로직을 최소한의 파라미터와 예외 처리만 남기도록 리팩터링
  - [x] `LikeRepository` 쿼리를 실제 사용 시나리오에 맞게 정리

- [ ] 신고/모더레이션 축소
  - [x] 리뷰 신고는 건수 집계와 자동 삭제(3회 이상)만 유지
  - [x] `ReviewModerationService`와 관련 DTO를 신고 사유/설명만 저장하도록 단순화
  - [x] 신고 API 응답은 성공 여부만 반환하도록 변경

- [ ] 테스트 강화
  - [x] 통합 테스트: Mock이 아닌 실제 JPA 레포지토리를 사용해 리뷰 작성/수정/삭제 및 좋아요 등록/취소/재등록이 정상 동작하는지 검증
  - [x] 단위 테스트: DTO 검증, Visit 자격 검증 실패 케이스, JWT 사용자 ID 전달 시나리오 테스트
  - [ ] 테스트 데이터 준비 시 `CustomUserDetails` 기반 userId/visitId를 셋업하는 유틸 작성 여부 검토

- [ ] 통계 연계 준비
  - [ ] (보류) 현재 이벤트 발행 로직 제거됨 — 통계 연계가 필요해지면 새로운 이벤트 설계부터 재검토
  - [ ] UC-Batch-01 자정 배치에서 필요한 집계 포인트(출발/도착/리뷰/좋아요, 시간대 통계, 예측 모델 재학습) 문서화
  - [ ] 신고 수 집계와 향후 통계 지표(예: 신고율) 간 연계 가능성을 별도 메모

## Relevant Files
- [x] `src/main/java/com/matjom/matjom/feed/dto/assembler/ReviewResponseAssembler.java` — 리뷰 응답에 작성자 이름만 안전하게 주입
- [x] `src/main/java/com/matjom/matjom/feed/repository/UserReadRepository.java` — 리뷰 응답에 작성자 이름 조회
- [x] `src/main/java/com/matjom/matjom/common/config/JpaConfig.java` — 테스트 및 Auditing 설정
- [x] `src/test/java/com/matjom/matjom/feed/service/LikeServiceIntegrationTest.java` — 테스트 및 Auditing 설정
- [x] `src/test/java/com/matjom/matjom/feed/service/LikeServiceTest.java` — 테스트 및 Auditing 설정
- [x] `src/test/java/com/matjom/matjom/feed/service/ReviewServiceIntegrationTest.java` — 테스트 및 Auditing 설정
- [x] `src/test/java/com/matjom/matjom/feed/service/ReviewServiceTest.java` — 테스트 및 Auditing 설정
- [x] `src/main/java/com/matjom/matjom/common/security/CustomUserDetails.java` — JWT 인증용 사용자 정보 래퍼
- [x] `src/main/java/com/matjom/matjom/feed/dto/response/LikeStatusResponseDTO.java` — 좋아요 응답 DTO
- [x] `src/main/java/com/matjom/matjom/feed/dto/request/LikeCreateRequestDTO.java` — 좋아요 핵심 구현
- [x] `src/main/java/com/matjom/matjom/feed/entity/likes/LikeStatus.java` — 좋아요 핵심 구현
- [x] `src/main/java/com/matjom/matjom/visit/repository/VisitReadRepository.java` — 방문 자격 검증 재사용 로직
- [x] `src/main/java/com/matjom/matjom/visit/service/VisitEligibilityChecker.java` — 방문 자격 검증 재사용 로직

- [x] `src/main/java/com/matjom/matjom/feed/dto/response/ReviewResponseDTO.java` — 리뷰 응답 DTO 최소화
- [x] 리뷰 상태 ENUM 제거 → BaseEntity `deleted_at`만 사용
- [x] `src/main/java/com/matjom/matjom/feed/entity/review/Review.java` — 리뷰 엔티티 정리 및 상태 단순화
- [x] `src/main/java/com/matjom/matjom/feed/service/ReviewService.java` — 방문 자격 검증과 리뷰 CRUD 단순화
- [x] `src/main/java/com/matjom/matjom/feed/repository/ReviewRepository.java` — 필요 메서드만 남기기
- [ ] `src/main/java/com/matjom/matjom/feed/controller/ReviewController.java` — JWT 기반 사용자 정보 처리 확인
- [x] `src/main/java/com/matjom/matjom/feed/entity/likes/Like.java` — 좋아요 엔티티 최소화
- [x] `src/main/java/com/matjom/matjom/feed/service/LikeService.java` — 좋아요 등록/취소/재활성화 로직 단순화
- [x] `src/main/java/com/matjom/matjom/feed/repository/LikeRepository.java` — 필요 쿼리만 유지
- [x] `src/main/java/com/matjom/matjom/moderation/ReviewModerationService.java` — 신고 건수 집계만 남기도록 리팩터링
- [x] `src/main/resources/schema-postgres.sql` — 리뷰/신고 테이블 스키마에서 불필요 필드/인덱스 제거
- [x] `src/test/java/com/matjom/matjom/moderation/ReviewModerationServiceTest.java` — 신고 건수 동작 검증

## Notes / Next Steps

- JWT `CustomUserDetails` 구현/주입 방식 확정 후 컨트롤러와 테스트 전반에 반영
- Visit 관련 서비스/리포지토리 인터페이스 정의 필요 (현재 TODO 상태)
- Redis 캐시/예측 모델 연동은 통계 단계에서 다룰 예정이므로, 현 단계에서는 TODO와 인터페이스 요건만 문서화
- 작업 진행 중 새로운 요구나 범위 변경이 발생하면 체크리스트에 항목을 추가하고 `Relevant Files`도 함께 갱신
