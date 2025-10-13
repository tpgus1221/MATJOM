# 리뷰 신고 API 가이드 (2025-10 최신)

## 개요
- **엔드포인트:** `POST /api/v1/reviews/{reviewId}/reports`
- **목적:** 사용자가 특정 리뷰에 대해 신고를 접수하면 신고 이력을 저장하고, 동일 리뷰 신고가 3회 이상 누적되면 리뷰를 자동 삭제합니다.
- **권한:** JWT 인증 필수. 컨트롤러는 `@RequestAttribute("userId")`로 신고자 UUID를 전달받습니다.

## 요청 스펙
```http
POST /api/v1/reviews/{reviewId}/reports
Content-Type: application/json
Authorization: Bearer {JWT}
```

```json
{
  "reason": "SPAM",            // 필수: ReportReason ENUM (SPAM, INAPPROPRIATE, FAKE, OFFENSIVE, OTHER)
  "description": "홍보성 댓글"  // 선택: 상세 설명 (최대 500자)
}
```

## 응답 스펙
```json
{
  "success": true,
  "data": null,
  "error": null,
  "timestamp": "2025-10-01T12:45:21.123Z"
}
```

- `success`: 신고가 정상적으로 접수되었는지 여부
- `data`: 신고 API는 별도 데이터를 반환하지 않으므로 항상 `null`
- `error`: 오류가 없을 경우 `null`
- `timestamp`: 응답 생성 시각 (UTC 기준)

## 예외 응답 요약

| 상황 | HTTP | 에러 코드 | 메시지 |
| ---- | ---- | --------- | ------- |
| 동일 사용자가 이미 신고한 경우 | 400 | `REVIEW_REPORT_ALREADY_EXISTS` | "이미 신고한 리뷰입니다." |
| 리뷰가 존재하지 않거나 삭제된 경우 | 404 | `REVIEW_NOT_FOUND` | "리뷰를 찾을 수 없습니다." |

공통 에러 포맷은 `공통응답_가이드.md`를 참고하세요.

## 프런트 핸들링 메모
1. 신고 성공 시 "신고가 접수되었습니다." 같은 토스트/팝업을 띄운다.
2. 동일 사용자의 재신고는 400 응답으로 차단되므로 에러 메시지를 토스트로 노출한다.
3. 삭제된 리뷰를 신고하려는 경우 404 응답을 받아 처리한다.

> 내부적으로는 신고 누적 건수를 계산해 3건 이상이 되면 리뷰를 자동으로 삭제합니다. 이 단계는 서버에서 처리되며 별도의 응답 값이 필요하지 않습니다.
