package com.matjom.matjom.visit.api;

import com.matjom.matjom.common.exception.base.SessionException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.common.security.CustomUserDetails;
import com.matjom.matjom.visit.dto.VisitManualArrivalRequest;
import com.matjom.matjom.visit.dto.VisitManualArrivalResponse;
import com.matjom.matjom.visit.dto.VisitPositionRequest;
import com.matjom.matjom.visit.dto.VisitPositionResponse;
import com.matjom.matjom.visit.dto.VisitSessionStartRequest;
import com.matjom.matjom.visit.dto.VisitSessionStartResponse;
import com.matjom.matjom.visit.service.VisitSessionService;
import com.matjom.matjom.visit.service.VisitPositionService;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/sessions")
@Validated
@RequiredArgsConstructor
public class VisitSessionController {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 200; // 멱등 키 최대 길이

    private final VisitSessionService visitSessionService;
    private final VisitPositionService visitPositionService;

    @PostMapping
	// [비즈니스 계약]
	// - 클라이언트가 "방문 세션"을 시작한다. 서버는 30분 TTL(만료 시각)을 부여하고 상태를 ACTIVE로 만든다.
	// - 헤더 Idempotency-Key는 필수: 네트워크 재시도/중복 탭에서도 "동일 세션 생성 요청"을 60초 내 같은 응답으로 재생(replay)하기 위함.
	// - 요청 DTO에는 '클라이언트 모드'(auto: 30s 주기 전송 / manual: 버튼 기반), 타깃 placeId, 사용자 위치(선택) 등이 포함될 수 있다.
	// - 반환은 sessionId/state/startedAt/expiresAt/replayed 를 표준 응답 포맷으로 감싼다.
    public ApiResponse<VisitSessionStartResponse> startSession(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody VisitSessionStartRequest request) {
        String sanitizedKey = validateIdempotencyKey(idempotencyKey); // 공백/길이 검증
        if (userDetails == null) {
            throw new SessionException(ErrorCode.UNAUTHORIZED);
        }
        VisitSessionStartResponse response = visitSessionService.startSession(request, userDetails.getUserId(), sanitizedKey); // 멱등 처리 포함 서비스 호출
        return ApiResponse.ok(response); // 공통 응답 포맷
    }

    @PostMapping("/{sessionId}/positions")
	// [비즈니스 계약]
	// - 활성 세션에 위치 이벤트(30초 주기 권장 또는 수동 전송)를 적재한다.
	// - 서비스는 저장과 동시에 자동 도착 판정을 시도할 수 있다(반경 30m + dwell 3분 + 이탈 10초 유예 정책 등, 실제 정책은 서비스/지오펜스에서 집행).
	// - 반환은 최근 판정 상태/누적 시간/도착 여부 등의 요약값(프로젝트 정책에 따름).
    public ApiResponse<VisitPositionResponse> recordPosition(
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody VisitPositionRequest request) {
        VisitPositionResponse response = visitPositionService.recordPosition(sessionId, request); //위치 이벤트 저장 + 자동 도착 판정
        return ApiResponse.ok(response);
    }

    @PostMapping("/{sessionId}/arrivals")
	// [비즈니스 계약]
	// - 수동 도착 확정. 자동 판정이 지연되거나 센서오차가 큰 경우, 사용자가 의도적으로 "도착"을 확정한다.
	// - 이 API 또한 멱등 헤더를 요구: 동일 버튼 중복 탭/재전송에서 중복 도착 처리를 방지.
	// - 서비스는 세션 상태를 ACTIVE→ARRIVED로 전이시키고, 권한 부여(리뷰/좋아요 등) 시그널을 발행할 수 있다.
	public ApiResponse<VisitManualArrivalResponse> confirmArrival(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody VisitManualArrivalRequest request) {
        String sanitizedKey = validateIdempotencyKey(idempotencyKey);
        VisitManualArrivalResponse response = visitSessionService.confirmManualArrival(sessionId, request, sanitizedKey); // 수동 도착도 멱등 키 필수
        return ApiResponse.ok(response);
    }

	// [헤더 검증 — 비즈니스 가드]
	// - 멱등키가 없으면 400: 클라의 재시도·중복 탭에서 "중복 세션/도착"이 발생할 수 있어 제품 정책상 필수.
	// - 과다 길이는 저장소 오염·로그 폭주·공격 벡터가 되므로 200자 상한으로 차단.
    private String validateIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) { // 헤더 누락 or 공백만이면 예외
            throw new SessionException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > IDEMPOTENCY_KEY_MAX_LENGTH) { // 200자 초과 방지
            throw new SessionException(ErrorCode.INVALID_REQUEST_PARAM, "Idempotency-Key 길이는 200자를 초과할 수 없습니다.");
        }
        return trimmed;
    }
}
