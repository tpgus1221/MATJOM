package com.matjom.matjom.visit.entity;

public enum VisitEventType {
	// [세션 시작] Visit가 생성되어 ACTIVE로 진입한 시점. startedAt 기준으로 1회 기록.
	// - 추천/리텐션 퍼널의 "세션 개시" 모수로 사용.
	// - meta 예: {"clientMode":"NAVIGATION","policy":{"ttlMinutes":30}}
	SESSION_STARTED,

	// [자동 도착 확정] 지오펜스+체류(dwell) 정책을 충족해 시스템이 ACTIVE→ARRIVED로 전이.
	// - 사용자는 버튼을 누르지 않음(센서·알고리즘 기반).
	// - meta 예: {"source":"AUTO","distance":12.4,"accuracy":8.0,"dwellSec":185}
	ARRIVED,

	// [수동 도착 확정] 사용자가 '도착 확정' 버튼을 눌러 ACTIVE→ARRIVED로 전이.
	// - confirmManualArrival API 성공 시 기록.
	// - meta 예: {"source":"MANUAL","requestedBy":"user","distance":9.7}
    ARRIVED_MANUAL,

	// [만료 종료] 세션 만료 시간(expiresAt) 도달로 ACTIVE→EXPIRED.
	// - 업로드 중지/세션 종료 UX 전환 근거.
	// - meta 예: {"reason":"timeout","ttlMinutes":30}
    EXPIRED,

	// [사용자/운영 취소] 정책에 따라 세션이 중도 종료될 때.
	// - 예: 사용자가 명시 취소, 관리자 강제 종료.
	// - meta 예: {"reason":"user_cancel"}
    CANCELLED,

	// [포괄 전이] 위 구체 타입으로 표현되지 않는 **특수 전이**에서만 사용.
	// - 예: 데이터 정정, 운영 툴에서의 일회성 수정 등.
	// - 주의: 남용하면 리포트가 흐려짐. 가능하면 구체 타입을 추가 정의해 대체.
    STATE_CHANGED
}
