package com.matjom.matjom.visit.entity;

public enum VisitStateEventSource {
    AUTO_ARRIVAL, // 시스템이 자동으로 도착 판정 (지오펜스+체류시간 충족)으로 상태 변경
    MANUAL_ARRIVAL, // 사용자가 직접 도착을 체크/버튼으로 표시했을 때
	TIMEOUT, // 세션이 시간 초과(EXPIRED)로 바뀌었을 때
    CANCEL, // 사용자가 세션을 취소했을 때
    SYSTEM // 시스템 내부 처리(예: 재시작, 강제 조정 등)로 상태가 바뀐 경우
}
