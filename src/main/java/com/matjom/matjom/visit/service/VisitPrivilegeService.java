package com.matjom.matjom.visit.service;

import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.user.entity.User;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
// [역할] '도착 확정(ARRIVED)' 직후, 사용자별 "장소 검색" 레이트리밋 버킷을 초기화한다.
// - 제품 의도: 매장 도착 후 이어지는 검색/탐색 플로우에서 사용자가 즉시 제한에 걸리지 않도록 UX 개선.
// - 호출 타이밍: VisitSessionService.confirmManualArrival(...) 또는 VisitPositionService.recordPosition(...)에서
//                상태가 ARRIVED로 전이된 직후 1회 호출(멱등).
// - 멱등성: Redis 키 삭제는 idempotent(키가 없으면 no-op). 중복 호출해도 부작용 없음.
public class VisitPrivilegeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VisitPrivilegeService.class);

	// [키 네임스페이스] 사용자 단위 장소 검색 레이트리밋 버킷의 Redis 키 prefix.
	// - 실제 키: "rl:places:user:{userUuid}"
	// - 삭제 의미: 해당 유저의 검색 카운터/윈도우를 "지금" 기준으로 리셋.
	private static final String RATE_LIMIT_USER_KEY_PREFIX = "rl:places:user:";

    private final StringRedisTemplate stringRedisTemplate;

    public VisitPrivilegeService(StringRedisTemplate stringRedisTemplate) {
		// [의존성] 레이트리밋 저장소 접근(문자열 기반 키/값). 멀티 노드에서도 일관 동작.
        this.stringRedisTemplate = Objects.requireNonNull(stringRedisTemplate, "stringRedisTemplate");
    }

	// [비즈니스 계약]
	// - 입력: 도착 확정된 Visit(세션). null이면 아무 일도 하지 않고 반환(보수적 방어).
	// - 동작: userId 기반 Redis 레이트리밋 키 삭제 → 검색 쿼터 즉시 회복.
	// - 실패 처리: warn 로그 후 예외 재전파(= 실패-폐쇄; 상위 트랜잭션이 있으면 롤백 가능).
    public void resetSearchQuotaForArrival(Visit visit) {
        if (visit == null) {
            return; // [방어] 예상치 못한 호출에도 시스템 안전성 우선
        }
        User visitUser = visit.getUser();
        if (visitUser == null) {
            return; // [데이터 가드] 유저 없는 세션은 정책상 비정상 → 여기서는 무시
        }
        UUID userId = visitUser.getId();
        if (userId == null) {
            return; // [스냅샷 가드] 사용자 ID 미존재 시 무시
        }

		// [키 구성] 사용자별 레이트리밋 버킷. 키가 없으면 삭제는 no-op.
        String key = RATE_LIMIT_USER_KEY_PREFIX + userId;
        try {
			// [리셋] 삭제 = "버킷 초기화" 의미. 멱등.
			// - delete(String)도 가능하지만, 리스트 인자 사용은 다수 키 확장 시 일관된 호출 패턴 유지 목적.
			stringRedisTemplate.delete(Collections.singletonList(key));
        } catch (RuntimeException ex) {
			// [운영 정책] 실패-폐쇄: 레이트리밋 리셋에 실패하면 상위에 예외 전파(알림/재시도 유도).
			// - 트랜잭션 경계 안이라면 호출부 트랜잭션이 롤백될 수 있음(설계 선택).
			LOGGER.warn("Failed to reset rate limit key for user {}", userId, ex);
            throw ex;
        }
    }
}
