package com.matjom.matjom.visit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjom.matjom.common.exception.base.PlaceException;
import com.matjom.matjom.common.exception.base.SessionException;
import com.matjom.matjom.common.exception.base.UserException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.idempotency.IdempotencyCallback;
import com.matjom.matjom.common.idempotency.IdempotencyResult;
import com.matjom.matjom.common.idempotency.IdempotencyStore;
import com.matjom.matjom.place.entity.Place;
import com.matjom.matjom.place.repository.PlaceJpaRepository;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;
import com.matjom.matjom.visit.dto.VisitManualArrivalRequest;
import com.matjom.matjom.visit.dto.VisitManualArrivalResponse;
import com.matjom.matjom.visit.dto.VisitSessionStartRequest;
import com.matjom.matjom.visit.dto.VisitSessionStartResponse;
import com.matjom.matjom.visit.entity.ClientMode;
import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitState;
import com.matjom.matjom.visit.entity.VisitStateEventSource;
import com.matjom.matjom.visit.repository.VisitRepository;
import com.matjom.matjom.visit.util.GeoDistanceCalculator;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitSessionService {

	// [멱등 네임스페이스] 세션 시작 요청용 키 prefix.
	// - 같은 Idempotency-Key라도 "세션 시작"과 "도착 확정"을 구분해 저장·재생하기 위함.
    private static final String IDEMPOTENCY_PREFIX = "idemp:sessions:start:";

	// [멱등 네임스페이스] 세션별 수동 도착 확정용 키 prefix.
	// - sessionId를 키에 포함해, 세션 A의 멱등 응답이 세션 B에 섞이지 않도록 격리.
    private static final String ARRIVAL_IDEMPOTENCY_PREFIX = "idemp:sessions:arrival:";

	// [세션 수명 정책] 세션 시작 시각 기준 30분 동안만 ACTIVE로 간주.
	// - 클라이언트는 이 만료시각을 근거로 위치 송신을 중단/정리.
    private static final long TIMEOUT_MINUTES = 30L;

	// [시간대 고정] 모든 세션 타임스탬프를 Asia/Seoul로 고정해 UX/운영 리포트의 지역 일관성 확보.
	private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

	// [수동 도착 정책] 최소/최대 체류 시간(초).
	// - 너무 빠른 도착 누름 방지(10분 미만) + 비정상 장기 세션 방지(60분 초과).
	private static final long MANUAL_ARRIVAL_MIN_SECONDS = 600L;
    private static final long MANUAL_ARRIVAL_MAX_SECONDS = 3600L;

	// [지오펜스 절대값] 수동 도착 허용 반경(미터). 자동 판정과 동일 기준 유지(30m).
	private static final double MANUAL_ARRIVAL_MAX_DISTANCE_METERS = 30.0;

    private final VisitRepository visitRepository;
    private final PlaceJpaRepository placeRepository;
    private final UserRepository userRepository;
    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;
    private final VisitStateTransitionRecorder stateTransitionRecorder;

    public VisitSessionService(VisitRepository visitRepository,
                               PlaceJpaRepository placeRepository,
                               UserRepository userRepository,
                               IdempotencyStore idempotencyStore,
                               ObjectMapper objectMapper,
                               VisitStateTransitionRecorder stateTransitionRecorder) {
        this.visitRepository = visitRepository;
        this.placeRepository = placeRepository;
        this.userRepository = userRepository;
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
        this.stateTransitionRecorder = Objects.requireNonNull(stateTransitionRecorder, "stateTransitionRecorder");
    }

    @Transactional
    public VisitSessionStartResponse startSession(final VisitSessionStartRequest request,
                                                  UUID userId,
                                                  String idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(userId, "userId");
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
        String requestHash = computeRequestHash(new SessionStartHashPayload(request, userId));

        IdempotencyResult<VisitSessionStartResponse> result = idempotencyStore.replayOrRun(
                redisKey,
                requestHash,
                VisitSessionStartResponse.class,
                new IdempotencyCallback<VisitSessionStartResponse>() {
                    @Override
                    public VisitSessionStartResponse execute() {
                        return createSession(request, userId);
                    }
                }
        );

        if (result.isReplayed()) {
            return markReplayed(result.getValue());
        }
        return result.getValue();
    }

    @Transactional
    public VisitManualArrivalResponse confirmManualArrival(Long sessionId,
                                                           VisitManualArrivalRequest request,
                                                           String idempotencyKey) {
		// [계약 가드] 수동 도착도 버튼 중복 탭 대비 멱등 필수.
		Objects.requireNonNull(idempotencyKey, "idempotencyKey");
		// [키 스코핑] 세션별로 멱등 응답을 격리하기 위해 sessionId를 키에 포함.
		String redisKey = ARRIVAL_IDEMPOTENCY_PREFIX + sessionId + ':' + idempotencyKey;
		// [동일성 정의] 같은 요청 바디면 같은 해시.
		String requestHash = computeRequestHash(request);

		// [멱등 실행] 60초 내 동일 요청이면 재생, 아니면 processManualArrival 1회 실행.
		IdempotencyResult<VisitManualArrivalResponse> result = idempotencyStore.replayOrRun(
                redisKey,
                requestHash,
                VisitManualArrivalResponse.class,
                new IdempotencyCallback<VisitManualArrivalResponse>() {
                    @Override
                    public VisitManualArrivalResponse execute() {
                        return processManualArrival(sessionId, request);
                    }
                }
        );

        if (result.isReplayed()) {
            return markManualReplayed(result.getValue());
        }
        return result.getValue();
    }

	// [세션 생성 규칙] 사용자·장소 존재 확인 → 사용자당 ACTIVE 중복 세션 금지 → 세션 수명 30분 부여.
    private VisitSessionStartResponse createSession(VisitSessionStartRequest request, UUID userId) {
        Long placeId = request.getPlaceId();

		// [존재 검증] 비정상 ID로 인한 유령 세션 방지.
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isEmpty()) {
            throw new UserException(ErrorCode.USER_NOT_FOUND);
        }
        User user = optionalUser.get();

        Optional<Place> optionalPlace = placeRepository.findById(placeId);
        if (optionalPlace.isEmpty()) {
            throw new PlaceException(ErrorCode.PLACE_NOT_FOUND);
        }
        Place place = optionalPlace.get();

		// [세션 단일성] 동일 사용자 ACTIVE 세션 1개 원칙. 중복 출발 방지.
        boolean activeExists = visitRepository.existsByUser_IdAndState(userId, VisitState.ACTIVE);
        if (activeExists) {
            throw new SessionException(ErrorCode.SESSION_ALREADY_EXISTS);
        }

		// [클라이언트 모드] 자동/수동 모드에 따라 위치 전송 주기 등 클라 가이드가 달라짐(서버는 상태만 기록).
        ClientMode mode = request.clientModeOrDefault();
		// [타임라인] 서버 기준 시작 시각과 만료 시각(30분)을 고정. Asia/Seoul 일관성 유지.
		OffsetDateTime startedAt = OffsetDateTime.now(DEFAULT_ZONE);

		// [엔티티 생성] 시작/만료 포함해 ACTIVE로 초기화.
		Visit visit = new Visit(user, place, mode, startedAt);
        OffsetDateTime expiresAt = startedAt.plusMinutes(TIMEOUT_MINUTES);
        visit.setExpiredAt(expiresAt);
        Visit saved = visitRepository.save(visit);

        return new VisitSessionStartResponse(saved.getId(), saved.getState(), saved.getStartedAt(), expiresAt, false);
    }

    private VisitSessionStartResponse markReplayed(VisitSessionStartResponse original) {
        return new VisitSessionStartResponse(
                original.sessionId(),
                original.state(),
                original.startedAt(),
                original.expiresAt(),
                true
        );
    }

	// [수동 도착 규칙] 상태·시간·거리 3가지를 모두 통과해야 도착을 확정.
	private VisitManualArrivalResponse processManualArrival(Long sessionId, VisitManualArrivalRequest request) {
        Optional<Visit> optionalVisit = visitRepository.findById(sessionId);
        if (optionalVisit.isEmpty()) {
            throw new SessionException(ErrorCode.SESSION_NOT_FOUND);
        }
        Visit visit = optionalVisit.get();

		// [감사 목적] 전이 전 상태를 보관해 이후 기록에 남김.
		VisitState previousState = visit.getState();

		// [상태 가드] ACTIVE가 아니면 도착 확정 불가(이미 종료/만료/도착 세션 보호).
		if (visit.getState() != VisitState.ACTIVE) {
            throw new SessionException(ErrorCode.SESSION_ALREADY_INACTIVE);
        }

		// [체류 시간 규칙] 시작 이후 10분~60분 사이에만 수동 도착 허용.
		OffsetDateTime startedAt = visit.getStartedAt();
        OffsetDateTime now = OffsetDateTime.now(DEFAULT_ZONE);
        long elapsedSeconds = Duration.between(startedAt, now).getSeconds();
        if (elapsedSeconds < MANUAL_ARRIVAL_MIN_SECONDS || elapsedSeconds > MANUAL_ARRIVAL_MAX_SECONDS) {
            throw new SessionException(ErrorCode.ARRIVAL_TIME_INVALID);
        }

		// [지오펜스 규칙] 장소 기준 30m 이내여야 수동 도착 허용. (GPS 오차를 고려한 완충치로 30m 채택)
		Place place = visit.getPlace();
        BigDecimal placeLat = place.getLatitude();
        BigDecimal placeLng = place.getLongitude();
        double distanceMeters = GeoDistanceCalculator.distanceMeters(
                placeLat,
                placeLng,
                request.getLatitude(),
                request.getLongitude());
        if (distanceMeters > MANUAL_ARRIVAL_MAX_DISTANCE_METERS) {
            throw new SessionException(ErrorCode.ARRIVAL_DISTANCE_EXCEEDED);
        }

		// [최신 위치 스냅샷] 도착 시점의 마지막 좌표/정확도 기록(사후 분쟁 대비·분석용).
        visit.updateLastPosition(request.getLatitude(), request.getLongitude(), request.getAccuracyMeters(), now);

		// [상태 전이] ACTIVE → ARRIVED, arrivedAt 기록.
		visit.arriveAt(now);
        visitRepository.save(visit);

		// [감사 이벤트] 누가(Manual), 언제, 이전→이후 상태를 영속 기록.
		stateTransitionRecorder.record(visit, previousState, visit.getState(), VisitStateEventSource.MANUAL_ARRIVAL, now);

        return new VisitManualArrivalResponse(
                visit.getId(),
                visit.getState(),
                visit.getArrivedAt(),
                request.getRequestedBy(),
                false
        );
    }

    private VisitManualArrivalResponse markManualReplayed(VisitManualArrivalResponse original) {
        return new VisitManualArrivalResponse(
                original.sessionId(),
                original.state(),
                original.arrivedAt(),
                original.requestedBy(),
                true
        );
    }

    private static final class SessionStartHashPayload {
        private final UUID userId;
        private final Long placeId;
        private final String clientNote;
        private final ClientMode clientMode;

        private SessionStartHashPayload(VisitSessionStartRequest request, UUID userId) {
            this.userId = userId;
            this.placeId = request.getPlaceId();
            this.clientNote = request.getClientNote();
            this.clientMode = request.getClientMode();
        }

        public UUID getUserId() {
            return userId;
        }

        public Long getPlaceId() {
            return placeId;
        }

        public String getClientNote() {
            return clientNote;
        }

        public ClientMode getClientMode() {
            return clientMode;
        }
    }

    private String computeRequestHash(Object request) {
        byte[] jsonBytes = toJsonBytes(request);
        MessageDigest digest = messageDigest();
        byte[] hashed = digest.digest(jsonBytes);
        StringBuilder builder = new StringBuilder(hashed.length * 2);
        for (byte value : hashed) {
            int unsigned = value & 0xFF;
            String hex = Integer.toHexString(unsigned);
            if (hex.length() == 1) {
                builder.append('0');
            }
            builder.append(hex);
        }
        return builder.toString();
    }

    private byte[] toJsonBytes(Object request) {
        try {
            return objectMapper.writeValueAsBytes(request);
        } catch (JsonProcessingException ex) {
            throw new SessionException(ErrorCode.INTERNAL_SERVER_ERROR, "세션 요청 직렬화에 실패했습니다.");
        }
    }

    private MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 해시 함수를 사용할 수 없습니다.", ex);
        }
    }
}
